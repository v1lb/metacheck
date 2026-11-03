package com.example.metacheck.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.metacheck.data.parser.C2paDetector
import com.example.metacheck.data.parser.ExifReader
import com.example.metacheck.data.parser.ExifResult
import com.example.metacheck.data.parser.ImageFormatDetector
import com.example.metacheck.data.parser.PngTextParser
import com.example.metacheck.data.parser.XmpExtractor
import com.example.metacheck.data.parser.readBounded
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
        }
    }

    private fun analyzeBlocking(uri: Uri): MetaReport {
        // Только content://. file:// позволил бы подсунуть путь к приватным файлам приложения.
        ensure(uri.scheme == ContentResolver.SCHEME_CONTENT, FailureCode.INVALID_URI)

        val (name, size) = queryNameAndSize(uri)
        // Пустые имя и MIME интерфейс показывает как «без имени» / «неизвестно» на языке пользователя.
        val mime = resolver.getType(uri).orEmpty()

        // Размер известен заранее: буфер выделяется сразу, без удвоений и лишней копии.
        val (bytes, truncated) = resolver.openInputStream(uri)?.use { readBounded(it, MAX_SCAN_BYTES, size) }
            ?: throw AppFailure(FailureCode.CANNOT_READ_SOURCE)

        val format = ImageFormatDetector.detect(bytes)
        if (format == ImageFormat.UNKNOWN) throw UnsupportedFormatException()

        val png = if (format == ImageFormat.PNG) PngTextParser.parse(bytes) else emptyList()
        val xmp = XmpExtractor.extractPacket(bytes, png)?.let(XmpExtractor::parse)
        // Android 10+ вырезает GPS из обычных потоков MediaStore. Читаем EXIF из «оригинального» URI.
        val exif = readExifWithLocation(uri)
        val hasC2pa = C2paDetector.looksPresent(bytes)

        val xmpTools = listOfNotNull(
            xmp?.fields?.get(XmpExtractor.KEY_CREATOR_TOOL)?.let { MetaItem("XMP CreatorTool", it) },
            xmp?.fields?.get(XmpExtractor.KEY_SOFTWARE_AGENT)?.let { MetaItem("XMP softwareAgent", it) },
        )
        val xmpItems = xmp?.items.orEmpty()
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
                hasC2pa = hasC2pa,
                hasGps = exif.gps != null,
                hasAnyMetadata = exif.hasData || xmpItems.isNotEmpty() || pngItems.isNotEmpty(),
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
            val id = if (parts.size == 2 && parts[0] == "image") parts[1].toLongOrNull() else null
            if (id != null) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id) else null
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
