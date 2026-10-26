package com.example.metacheck.data.metadata

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.metacheck.domain.metadata.BatchProgress
import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataRewriteRepository
import com.example.metacheck.domain.metadata.MetadataWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * File writes operate on private staging files, never on the user's image in place.
 * SAF replacement requires an explicit user-selected backup document. Android
 * document providers do not guarantee atomic replacement; rollback is best-effort.
 */
class MetadataRewriteRepositoryImpl(private val context: Context) : MetadataRewriteRepository {
    private val resolver = context.contentResolver

    override suspend fun stripCopy(source: String, destination: String): MetadataWriteResult = withContext(Dispatchers.IO) {
        val src = checkedUri(source); val dst = checkedUri(destination)
        require(src != dst) { "Оригинал нельзя использовать как новый файл" }
        val stage = createTemp("strip", ".img")
        try {
            val prepared = renderClean(src, stage)
            require(verifyNoMetadata(stage, prepared.mime)) { "Проверка очистки метаданных не пройдена" }
            writeDocument(dst, stage, allowNewFileFallback = true)
            // A copied image is NOT a successful privacy operation until the
            // bytes persisted by the document provider are read back intact.
            if (!verifySameFile(stage, dst)) {
                throw IOException("Не удалось подтвердить сохранённый файл. Проверьте и удалите неполную копию")
            }
            prepared.asResult(stage.length(), verified = true)
        } finally { stage.delete() }
    }

    /** Lossless JPEG GPS removal; XMP/IPTC/COM are removed to avoid duplicate hidden coordinates.
     * PNG is re-encoded without metadata because editing isolated PNG GPS is not supported.
     */
    override suspend fun wipeGpsCopy(source: String, destination: String): MetadataWriteResult = withContext(Dispatchers.IO) {
        val src = checkedUri(source); val dst = checkedUri(destination)
        require(src != dst) { "Выберите новый файл для очищенной копии" }
        val stage = createTemp("gps_wipe", ".img")
        try {
            val prepared = when (readMime(src)) {
                "image/jpeg" -> {
                    prepareEdited(src, stage, MetadataPatch(removeGps = true))
                    val exif = ExifInterface(stage.absolutePath)
                    require(exif.latLong == null && GPS_TAGS.all { exif.getAttribute(it) == null }) {
                        "Проверка GPS-тегов не пройдена"
                    }
                    Prepared("image/jpeg", false)
                }
                "image/png" -> {
                    val result = renderClean(src, stage)
                    require(verifyNoMetadata(stage, result.mime)) { "Проверка очистки PNG не пройдена" }
                    result
                }
                else -> throw IOException("Удаление GPS поддерживает JPEG и PNG")
            }
            writeDocument(dst, stage, allowNewFileFallback = true)
            if (!verifySameFile(stage, dst)) throw IOException("Не удалось проверить сохранённый очищенный файл")
            prepared.asResult(stage.length(), true)
        } finally { stage.delete() }
    }

    override suspend fun editCopy(source: String, destination: String, patch: MetadataPatch): MetadataWriteResult = withContext(Dispatchers.IO) {
        val src = checkedUri(source); val dst = checkedUri(destination)
        require(src != dst) { "Оригинал нельзя использовать как новый файл" }
        val stage = createTemp("edit", ".jpg")
        try {
            prepareEdited(src, stage, patch)
            writeDocument(dst, stage, allowNewFileFallback = true)
            if (!verifySameFile(stage, dst)) {
                throw IOException("Не удалось подтвердить изменения EXIF в сохранённом файле")
            }
            MetadataWriteResult(stage.length(), false, "JPEG", verified = true)
        } finally { stage.delete() }
    }

    override suspend fun overwriteWithBackup(
        source: String,
        backupDestination: String,
        patch: MetadataPatch?,
    ): MetadataWriteResult = withContext(Dispatchers.IO) {
        val original = checkedUri(source); val backup = checkedUri(backupDestination)
        require(original != backup) { "Выберите другой файл для резервной копии" }
        val originalTemp = createTemp("original", ".bak")
        val prepared = createTemp("replacement", ".img")
        try {
            val originalMime = readMime(original)
            if (patch == null) {
                require(originalMime == "image/jpeg" || originalMime == "image/png") {
                    "Перезапись при очистке поддерживает только JPEG и PNG: другие форматы сохраняйте как новую копию"
                }
            } else require(originalMime == "image/jpeg") { "Редактирование EXIF с перезаписью поддерживается для JPEG" }
            copyUriToFile(original, originalTemp)
            // External backup must be complete and independently readable before touching the original.
            writeDocument(backup, originalTemp, allowNewFileFallback = true)
            if (!verifySameFile(originalTemp, backup)) throw IOException("Невозможно проверить резервную копию. Оригинал не изменён")
            val result = if (patch == null) renderClean(original, prepared) else {
                prepareEdited(original, prepared, patch)
                Prepared("image/jpeg", false)
            }
            if (patch == null && !verifyNoMetadata(prepared, result.mime)) {
                throw IOException("Результат очистки не прошёл проверку")
            }
            var mutationAttempted = false
            try {
                mutationAttempted = true
                writeDocument(original, prepared)
                if (!verifySameFile(prepared, original)) throw IOException("Невозможно проверить заменённый файл")
            } catch (e: Exception) {
                if (mutationAttempted) {
                    try {
                        writeDocument(original, originalTemp)
                        if (!verifySameFile(originalTemp, original)) throw IOException("Проверка восстановления не пройдена")
                    } catch (restoreError: Exception) {
                        throw IOException(
                            "Ошибка замены; автоматическое восстановление не удалось. Резервная копия сохранена в выбранном вами месте. " +
                                "Не удаляйте её. Причина: ${restoreError.message}", e,
                        )
                    }
                }
                throw IOException("Замена отменена; оригинал восстановлен из резервной копии: ${e.message}", e)
            }
            result.asResult(prepared.length(), true)
        } finally { originalTemp.delete(); prepared.delete() }
    }

    override suspend fun stripBatch(
        sources: List<String>, destinationTree: String, progress: (BatchProgress) -> Unit,
    ): BatchProgress = withContext(Dispatchers.IO) {
        require(sources.size in 1..50) { "За один запуск: от 1 до 50 фотографий" }
        val tree = checkedUri(destinationTree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var report = BatchProgress(total = sources.size, running = true)
        progress(report)
        for ((index, raw) in sources.withIndex()) {
            coroutineContext.ensureActive()
            val label = runCatching { displayName(checkedUri(raw)) }.getOrDefault("photo_${index + 1}")
            report = report.copy(current = label)
            progress(report)
            val stage = createTemp("batch", ".img")
            var target: Uri? = null
            try {
                val prepared = renderClean(checkedUri(raw), stage)
                if (!verifyNoMetadata(stage, prepared.mime)) throw IOException("Проверка очистки не пройдена")
                val ext = if (prepared.mime == "image/jpeg") "jpg" else "png"
                val name = uniqueName(label, ext, index)
                target = DocumentsContract.createDocument(resolver, root, prepared.mime, name)
                    ?: throw IOException("Нельзя создать файл в выбранной папке")
                writeDocument(target, stage, allowNewFileFallback = true)
                if (!verifySameFile(stage, target)) throw IOException("Не удалось проверить сохранённый файл")
                report = report.copy(succeeded = report.succeeded + 1)
            } catch (e: CancellationException) {
                target?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                throw e
            } catch (e: Exception) {
                target?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                val reason = (e.message ?: e.javaClass.simpleName).take(130)
                report = report.copy(
                    failed = report.failed + 1,
                    errors = (report.errors + "${label.take(80)}: $reason").takeLast(50),
                )
            } finally {
                stage.delete()
            }
            report = report.copy(completed = index + 1)
            progress(report)
        }
        report.copy(running = false, current = "").also(progress)
    }

    private fun checkedUri(value: String): Uri = Uri.parse(value).also {
        require(it.scheme == ContentResolver.SCHEME_CONTENT) { "Используйте документ из Android-файлов" }
    }

    private fun createTemp(prefix: String, suffix: String): File =
        File.createTempFile("meta_${prefix}_", suffix, context.cacheDir)

    private fun readMime(uri: Uri): String {
        val contentType = resolver.getType(uri)?.lowercase(Locale.ROOT)
        resolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(12)
            val count = stream.read(header)
            if (count >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) return "image/jpeg"
            if (count >= 8 && header.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() }) return "image/png"
        }
        // Never trust a filename or provider MIME type for in-place replacements.
        throw IOException("Исходный формат не подтверждён сигнатурой (тип провайдера: ${contentType ?: "не указан"})")
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        resolver.openInputStream(uri)?.use { stream ->
            FileOutputStream(target).use { sink -> copyLimited(stream, sink) }
        } ?: throw IOException("Не удалось прочитать исходный файл")
    }

    private fun copyLimited(source: InputStream, target: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = source.read(buffer)
            if (n < 0) return
            if (n == 0) continue
            total += n
            if (total > MAX_SOURCE_BYTES) throw IOException("Размер изображения превышает 80 МБ")
            target.write(buffer, 0, n)
        }
    }

    private fun prepareEdited(source: Uri, output: File, patch: MetadataPatch) {
        val sourceMime = readMime(source)
        require(sourceMime == "image/jpeg") {
            "Редактор EXIF работает с JPEG; PNG/HEIC/AVIF пока можно полностью очистить как новую копию"
        }
        val original = createTemp("editable_original", ".jpg")
        try {
            copyUriToFile(source, original)
            JpegSegments.removeContradictorySegments(original, output)
            val exif = ExifInterface(output.absolutePath)
            if (patch.removeGps || (patch.latitude != null && patch.longitude != null)) {
                GPS_TAGS.forEach { exif.setAttribute(it, null) }
            }
            if (patch.latitude != null && patch.longitude != null) {
                exif.setLatLong(patch.latitude, patch.longitude)
            }
            patch.dateTaken?.let { exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it) }
            patch.dateDigitized?.let { exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, it) }
            patch.make?.let { exif.setAttribute(ExifInterface.TAG_MAKE, it) }
            patch.model?.let { exif.setAttribute(ExifInterface.TAG_MODEL, it) }
            patch.author?.let { exif.setAttribute(ExifInterface.TAG_ARTIST, it) }
            patch.copyright?.let { exif.setAttribute(ExifInterface.TAG_COPYRIGHT, it) }
            patch.description?.let { exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
            patch.iso?.let { exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString()) }
            patch.aperture?.let { exif.setAttribute(ExifInterface.TAG_F_NUMBER, it.toString()) }
            patch.exposureSeconds?.let { exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, it.toString()) }
            exif.saveAttributes()
            val check = ExifInterface(output.absolutePath)
            if (patch.removeGps && check.latLong != null) throw IOException("EXIF GPS не удалён")
            if (patch.latitude != null && patch.longitude != null) {
                val pair = check.latLong ?: throw IOException("EXIF GPS не записан")
                if (kotlin.math.abs(pair[0] - patch.latitude) > .0001 || kotlin.math.abs(pair[1] - patch.longitude) > .0001) {
                    throw IOException("GPS отличается от заданных координат")
                }
            }
            for ((tag, desired) in listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL to patch.dateTaken,
                ExifInterface.TAG_DATETIME_DIGITIZED to patch.dateDigitized,
                ExifInterface.TAG_MAKE to patch.make,
                ExifInterface.TAG_MODEL to patch.model,
                ExifInterface.TAG_ARTIST to patch.author,
                ExifInterface.TAG_COPYRIGHT to patch.copyright,
                ExifInterface.TAG_IMAGE_DESCRIPTION to patch.description,
            )) {
                if (desired != null && check.getAttribute(tag) != desired) {
                    throw IOException("EXIF проверка поля $tag не пройдена")
                }
            }
            if (output.length() == 0L) throw IOException("Пустой результат редактирования")
        } finally { original.delete() }
    }

    private data class Prepared(val mime: String, val downscaled: Boolean) {
        fun asResult(bytes: Long, verified: Boolean) = MetadataWriteResult(
            bytes, downscaled, if (mime == "image/jpeg") "JPEG" else "PNG", verified,
        )
    }

    private fun renderClean(source: Uri, output: File): Prepared {
        // Stage the input with a size bound before passing it to an image decoder.
        val raw = createTemp("clean_source", ".bin")
        try {
            copyUriToFile(source, raw)
            val sourceMime = runCatching { readMime(source) }.getOrNull()
            val mime = if (sourceMime == "image/jpeg") "image/jpeg" else "image/png"
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Неподдерживаемый формат изображения")
            var sample = 1
            while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MAX_PIXELS) sample *= 2
            val orientation = runCatching {
                ExifInterface(raw.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val bitmap = BitmapFactory.decodeFile(raw.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("Не удалось прочитать пиксели. Попробуйте другое изображение")
            var oriented = bitmap
            try {
                oriented = applyExifOrientation(bitmap, orientation)
                FileOutputStream(output).use { stream ->
                    val format = if (mime == "image/jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    if (!oriented.compress(format, 95, stream)) throw IOException("Кодирование изображения не удалось")
                    stream.fd.sync()
                }
                if (output.length() == 0L) throw IOException("Создан пустой файл")
            } finally {
                if (oriented !== bitmap) oriented.recycle()
                bitmap.recycle()
            }
            return Prepared(mime, sample > 1)
        } catch (outOfMemory: OutOfMemoryError) {
            throw IOException("Недостаточно памяти для обработки изображения", outOfMemory)
        } finally { raw.delete() }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun verifyNoMetadata(source: File, mime: String): Boolean =
        if (mime == "image/jpeg") JpegSegments.isStrippedJpeg(source) else hasOnlyImagePngChunks(source)

    private fun hasOnlyImagePngChunks(file: File): Boolean {
        BufferedInputStream(FileInputStream(file)).use { input ->
            val signature = ByteArray(8)
            if (input.read(signature) != 8 || signature.toList() != listOf(137,80,78,71,13,10,26,10).map { it.toByte() }) return false
            while (true) {
                val header = ByteArray(8)
                if (input.read(header) != 8) return false
                val length = ((header[0].toLong() and 255) shl 24) or
                    ((header[1].toLong() and 255) shl 16) or
                    ((header[2].toLong() and 255) shl 8) or (header[3].toLong() and 255)
                if (length > 200_000_000L) return false
                val type = String(header, 4, 4, Charsets.US_ASCII)
                if (type in setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME", "iCCP")) return false
                if (!skipAll(input, length + 4)) return false
                if (type == "IEND") return true
            }
        }
    }

    private fun skipAll(input: InputStream, amount: Long): Boolean {
        var left = amount
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) left -= skipped
            else if (input.read() < 0) return false else left--
        }
        return true
    }

    private fun writeDocument(uri: Uri, staged: File, allowNewFileFallback: Boolean = false) {
        // "rwt" is mandatory for overwriting an existing original: it must truncate.
        // Some SAF providers accept only OutputStream("wt") for freshly created documents.
        val descriptor = runCatching { resolver.openFileDescriptor(uri, "rwt") }.getOrNull()
        if (descriptor != null) {
            descriptor.use { pfd ->
                FileInputStream(staged).use { source ->
                    FileOutputStream(pfd.fileDescriptor).use { destination ->
                        source.copyTo(destination, bufferSize = 64 * 1024)
                        destination.fd.sync()
                    }
                }
            }
            return
        }
        if (!allowNewFileFallback) throw IOException("Провайдер не поддерживает безопасную перезапись (rwt)")
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Невозможно открыть новый файл для записи")
        stream.use { destination -> FileInputStream(staged).use { it.copyTo(destination, 64 * 1024) } }
    }

    private fun verifySameFile(staged: File, uri: Uri): Boolean = runCatching {
        FileInputStream(staged).use { expected ->
            resolver.openInputStream(uri)?.use { saved -> WriteIntegrity.matches(expected, saved) } ?: false
        }
    }.getOrDefault(false)

    private fun displayName(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) ?: "image" else "image"
    } ?: "image"

    private fun uniqueName(raw: String, ext: String, index: Int): String {
        val base = raw.substringBeforeLast('.', raw).replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9_-]"), "_").take(55).ifBlank { "photo" }
        return "${base}_clean_${System.currentTimeMillis()}_${index + 1}.$ext"
    }

    companion object {
        private const val MAX_SOURCE_BYTES = 80L * 1024L * 1024L
        private const val MAX_PIXELS = 16_000_000L
        private val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        )
    }
}
