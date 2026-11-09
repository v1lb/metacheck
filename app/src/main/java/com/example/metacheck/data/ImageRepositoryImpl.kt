package com.example.metacheck.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.metacheck.data.parser.C2paReader
import com.example.metacheck.data.parser.ExifReader
import com.example.metacheck.data.parser.ExifResult
import com.example.metacheck.data.parser.ImageFormatDetector
import com.example.metacheck.data.parser.PngTextParser
import com.example.metacheck.data.parser.IptcReader
import com.example.metacheck.data.parser.XmpExtractor
import com.example.metacheck.data.parser.readBounded
import com.example.metacheck.data.video.Mp4FormatException
import com.example.metacheck.data.video.Mp4MetadataReader
import com.example.metacheck.data.video.VideoFiles
import com.example.metacheck.data.video.VideoMetadata
import java.io.File
import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.analysis.SignalInput
import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.AppFailure
import com.example.metacheck.domain.FailureCode
import com.example.metacheck.domain.ensure
import com.example.metacheck.domain.model.TooLargeException
import com.example.metacheck.domain.model.ValueFormat
import com.example.metacheck.domain.model.UnsupportedFormatException
import com.example.metacheck.domain.repository.ImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageRepositoryImpl(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Папка для временной копии видео, если провайдер не даёт читать файл по месту. */
    private val cacheDir: File? = null,
) : ImageRepository {

    override suspend fun analyze(source: String): Result<MetaReport> = withContext(io) {
        try {
            Result.success(analyzeBlocking(Uri.parse(source)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            // Error, а не Exception: раньше он проходил мимо catch и ронял приложение.
            Result.failure(TooLargeException())
        } catch (e: StackOverflowError) {
            // Специально собранный файл (например, вложенные IFD в EXIF) не должен ронять приложение.
            Result.failure(AppFailure(FailureCode.CANNOT_READ_SOURCE))
        }
    }

    private fun analyzeBlocking(uri: Uri): MetaReport {
        // Только content://. file:// позволил бы подсунуть путь к приватным файлам приложения.
        ensure(uri.scheme == ContentResolver.SCHEME_CONTENT, FailureCode.INVALID_URI)

        val (name, size) = queryNameAndSize(uri)
        // Пустые имя и MIME интерфейс показывает как «без имени» / «неизвестно» на языке пользователя.
        val mime = resolver.getType(uri).orEmpty()

        // Видео читается выборочно (заголовки и moov), а не первые 16 МБ целиком.
        if (ImageFormatDetector.detect(VideoFiles.head(resolver, uri)).isVideo) return analyzeVideo(uri, name, size, mime)

        // Размер известен заранее: буфер выделяется сразу, без удвоений и лишней копии.
        val (bytes, truncated) = resolver.openInputStream(uri)?.use { readBounded(it, MAX_SCAN_BYTES, size) }
            ?: throw AppFailure(FailureCode.CANNOT_READ_SOURCE)

        val format = ImageFormatDetector.detect(bytes)
        if (format == ImageFormat.UNKNOWN) throw UnsupportedFormatException()

        val png = if (format == ImageFormat.PNG) PngTextParser.parse(bytes) else emptyList()
        val xmp = XmpExtractor.extractPacket(bytes, png)?.let(XmpExtractor::parse)
        // Android 10+ вырезает GPS из обычных потоков MediaStore. Читаем EXIF из «оригинального» URI.
        val exif = readExifWithLocation(uri)
        // Ошибка разбора C2PA не должна ломать анализ: тогда просто нет сведений из манифеста.
        val c2pa = try { C2paReader.read(bytes, format) } catch (_: Exception) { null }

        val xmpTools = listOfNotNull(
            xmp?.fields?.get(XmpExtractor.KEY_CREATOR_TOOL)?.let { MetaItem("XMP CreatorTool", it) },
            xmp?.fields?.get(XmpExtractor.KEY_SOFTWARE_AGENT)?.let { MetaItem("XMP softwareAgent", it) },
        )
        // IPTC-IIM (APP13): то же, что в XMP, но в старом формате. Повторы значений из XMP не показываем.
        val iptcItems = if (format == ImageFormat.JPEG) IptcReader.read(bytes) else emptyList()
        val xmpValues = xmp?.items.orEmpty().map { it.value.trim() }.toSet()
        val xmpItems = xmp?.items.orEmpty() + iptcItems.filter { it.value.trim() !in xmpValues }
        val pngItems = png
            .filter { it.keyword != "XML:com.adobe.xmp" }
            .map {
                if (it.text == PngTextParser.BAD_DATA) MetaItem(it.keyword, "", format = ValueFormat.BAD_DATA)
                else MetaItem(it.keyword, it.text)
            }

        val signals = SignalEngine.evaluate(
            SignalInput(
                toolFields = exif.toolFields + xmpTools,
                freeTextFields = exif.freeTextFields,
                xmpFields = xmp?.fields.orEmpty(),
                pngText = png,
                c2pa = c2pa,
                fileName = name,
                hasGps = exif.gps != null,
                hasAnyMetadata = exif.hasData || xmpItems.isNotEmpty() || pngItems.isNotEmpty() || c2pa != null,
                truncated = truncated,
            ),
        )

        return MetaReport(
            file = FileInfo(name, mime, size, format, bytes.size, truncated),
            exifSections = exif.sections,
            xmpItems = xmpItems,
            pngTextItems = pngItems,
            gps = exif.gps,
            signals = signals,
        )
    }


    private fun analyzeVideo(uri: Uri, name: String, size: Long, mime: String): MetaReport {
        val video = readVideoWithLocation(uri)
        val xmp = video.xmpPacket?.let(XmpExtractor::parse)
        val c2pa = video.c2paBox?.let { box -> try { C2paReader.read(box, ImageFormat.HEIC) } catch (_: Exception) { null } }
        val xmpTools = listOfNotNull(
            xmp?.fields?.get(XmpExtractor.KEY_CREATOR_TOOL)?.let { MetaItem("XMP CreatorTool", it) },
            xmp?.fields?.get(XmpExtractor.KEY_SOFTWARE_AGENT)?.let { MetaItem("XMP softwareAgent", it) },
        )
        val signals = SignalEngine.evaluate(
            SignalInput(
                toolFields = video.toolFields + xmpTools,
                freeTextFields = video.freeTextFields,
                xmpFields = xmp?.fields.orEmpty(),
                pngText = emptyList(),
                c2pa = c2pa,
                fileName = name,
                hasGps = video.gps != null,
                hasAnyMetadata = video.hasPersonalData || xmp?.items?.isNotEmpty() == true,
                truncated = false,
            ),
        )
        return MetaReport(
            file = FileInfo(name, mime, size, video.format, video.moovBytes, false),
            exifSections = video.sections,
            xmpItems = xmp?.items.orEmpty(),
            pngTextItems = emptyList(),
            gps = video.gps,
            signals = signals,
        )
    }

    /** Как и с фото, Android 10+ прячет место съёмки видео, если файл открыт не через «оригинальный» URI. */
    private fun readVideoWithLocation(uri: Uri): VideoMetadata {
        val candidates = ArrayList<Uri>()
        if (Build.VERSION.SDK_INT >= 29) {
            mediaStoreUri(uri)?.let { media ->
                candidates.add(try { MediaStore.setRequireOriginal(media) } catch (_: Exception) { media })
            }
        }
        candidates.add(uri)
        var fallback: VideoMetadata? = null
        var lastError: Exception? = null
        for (candidate in candidates) {
            val result = try {
                VideoFiles.open(resolver, candidate, cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp"))
                    .use { Mp4MetadataReader.read(it.source) }
            } catch (e: Mp4FormatException) {
                lastError = e; null
            } catch (e: Exception) {
                if (candidate == uri) lastError = e
                null
            } ?: continue
            if (result.gps != null) return result
            if (fallback == null) fallback = result
        }
        return fallback ?: throw when (lastError) {
            is Mp4FormatException, null -> UnsupportedFormatException()
            else -> lastError!!
        }
    }

    /**
     * Читает EXIF, пытаясь получить координаты без редактирования системой.
     * Фото из системного выбора файлов приходят как content://com.android.providers.media.documents/...,
     * а MediaStore.setRequireOriginal принимает только content://media/... — поэтому URI нужно преобразовать.
     * Без этого шага (и без разрешения ACCESS_MEDIA_LOCATION) Android отдаёт файл без GPS.
     */
    private fun readExifWithLocation(uri: Uri): ExifResult {
        val candidates = ArrayList<Uri>()
        if (Build.VERSION.SDK_INT >= 29) {
            mediaStoreUri(uri)?.let { media ->
                candidates.add(try { MediaStore.setRequireOriginal(media) } catch (_: Exception) { media })
            }
        }
        candidates.add(uri)

        var fallback: ExifResult? = null
        for (candidate in candidates) {
            val result = try {
                resolver.openInputStream(candidate)?.use { ExifReader.read(it) }
            } catch (_: Exception) {
                null
            } ?: continue
            if (result.gps != null) return result
            if (fallback == null || (!fallback.hasData && result.hasData)) fallback = result
        }
        return fallback ?: ExifReader.EMPTY
    }

    private fun mediaStoreUri(uri: Uri): Uri? = when (uri.authority) {
        MediaStore.AUTHORITY -> uri
        MEDIA_DOCUMENTS_AUTHORITY -> try {
            val parts = DocumentsContract.getDocumentId(uri).split(':')
            val id = if (parts.size == 2) parts[1].toLongOrNull() else null
            when {
                id == null -> null
                parts[0] == "image" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                parts[0] == "video" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        else -> null
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = ""
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it.take(255) }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    companion object {
        /** Читаем не больше 16 МБ: этого хватает для заголовков и текстовых блоков до данных изображения. */
        const val MAX_SCAN_BYTES = 16 * 1024 * 1024
        private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
    }
}
