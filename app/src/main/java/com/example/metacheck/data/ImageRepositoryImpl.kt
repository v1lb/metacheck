package com.example.metacheck.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.metacheck.data.parser.C2paDetector
import com.example.metacheck.data.parser.ExifReader
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
import com.example.metacheck.domain.model.UnsupportedFormatException
import com.example.metacheck.domain.repository.ImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
        }
    }

    private fun analyzeBlocking(uri: Uri): MetaReport {
        // Только content://. file:// позволил бы подсунуть путь к приватным файлам приложения.
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) { "Недопустимый источник файла" }

        val (name, size) = queryNameAndSize(uri)
        val mime = resolver.getType(uri) ?: "неизвестно"

        val (bytes, truncated) = resolver.openInputStream(uri)?.use { readBounded(it, MAX_SCAN_BYTES) }
            ?: throw IOException("Не удалось открыть файл")

        val format = ImageFormatDetector.detect(bytes)
        if (format == ImageFormat.UNKNOWN) throw UnsupportedFormatException()

        val png = if (format == ImageFormat.PNG) PngTextParser.parse(bytes) else emptyList()
        val xmp = XmpExtractor.extractPacket(bytes, png)?.let(XmpExtractor::parse)
        val exif = resolver.openInputStream(uri)?.use { ExifReader.read(it) } ?: ExifReader.EMPTY
        val hasC2pa = C2paDetector.looksPresent(bytes)

        val xmpTools = listOfNotNull(
            xmp?.fields?.get(XmpExtractor.KEY_CREATOR_TOOL)?.let { MetaItem("XMP CreatorTool", it) },
            xmp?.fields?.get(XmpExtractor.KEY_SOFTWARE_AGENT)?.let { MetaItem("XMP softwareAgent", it) },
        )
        val xmpItems = xmp?.items.orEmpty()
        val pngItems = png
            .filter { it.keyword != "XML:com.adobe.xmp" }
            .map { MetaItem(it.keyword, it.text) }

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

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "без имени"
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
    }
}
