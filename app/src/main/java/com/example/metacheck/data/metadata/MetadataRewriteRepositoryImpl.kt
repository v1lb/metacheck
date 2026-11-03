package com.example.metacheck.data.metadata

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.metacheck.domain.AppFailure
import com.example.metacheck.domain.FailureCode
import com.example.metacheck.domain.HiddenPart
import com.example.metacheck.domain.ensure
import com.example.metacheck.domain.metadata.BatchFileError
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
        ensure(src != dst, FailureCode.SAME_AS_SOURCE)
        val stage = createTemp("strip", ".img")
        try {
            val prepared = renderClean(src, stage)
            ensure(verifyNoMetadata(stage, prepared.mime), FailureCode.CLEAN_CHECK_FAILED)
            writeDocument(dst, stage, allowNewFileFallback = true)
            // A copied image is NOT a successful privacy operation until the
            // bytes persisted by the document provider are read back intact.
            if (!verifySameFile(stage, dst)) {
                throw AppFailure(FailureCode.SAVED_FILE_UNVERIFIED)
            }
            prepared.asResult(stage.length(), verified = true)
        } finally { stage.delete() }
    }

    /** Lossless JPEG GPS removal; XMP/IPTC/COM are removed to avoid duplicate hidden coordinates.
     * PNG is re-encoded without metadata because editing isolated PNG GPS is not supported.
     */
    override suspend fun wipeGpsCopy(source: String, destination: String): MetadataWriteResult = withContext(Dispatchers.IO) {
        val src = checkedUri(source); val dst = checkedUri(destination)
        ensure(src != dst, FailureCode.SAME_AS_SOURCE)
        val stage = createTemp("gps_wipe", ".img")
        try {
            val prepared = when (readMime(src)) {
                "image/jpeg" -> {
                    prepareEdited(src, stage, MetadataPatch(removeGps = true))
                    val exif = ExifInterface(stage.absolutePath)
                    ensure(exif.latLong == null && GPS_TAGS.all { exif.getAttribute(it) == null }, FailureCode.GPS_NOT_REMOVED)
                    // Координаты прячутся не только в EXIF: Motion Photo, MPF-кадры, XMP, миниатюра.
                    val hidden = JpegSegments.inspect(stage)
                    ensure(!hidden.hasHiddenPayload, FailureCode.HIDDEN_DATA_FOUND, *hidden.parts())
                    Prepared("image/jpeg", false)
                }
                "image/png" -> {
                    val result = renderClean(src, stage)
                    ensure(verifyNoMetadata(stage, result.mime), FailureCode.CLEAN_CHECK_FAILED)
                    result
                }
                else -> throw AppFailure(FailureCode.WIPE_GPS_JPEG_PNG_ONLY)
            }
            writeDocument(dst, stage, allowNewFileFallback = true)
            if (!verifySameFile(stage, dst)) throw AppFailure(FailureCode.SAVED_FILE_UNVERIFIED)
            prepared.asResult(stage.length(), true)
        } finally { stage.delete() }
    }

    override suspend fun editCopy(source: String, destination: String, patch: MetadataPatch): MetadataWriteResult = withContext(Dispatchers.IO) {
        val src = checkedUri(source); val dst = checkedUri(destination)
        ensure(src != dst, FailureCode.SAME_AS_SOURCE)
        val stage = createTemp("edit", ".jpg")
        try {
            prepareEdited(src, stage, patch)
            writeDocument(dst, stage, allowNewFileFallback = true)
            if (!verifySameFile(stage, dst)) {
                throw AppFailure(FailureCode.EDIT_UNVERIFIED)
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
        ensure(original != backup, FailureCode.SAME_AS_BACKUP)
        val originalTemp = createTemp("original", ".bak")
        val prepared = createTemp("replacement", ".img")
        try {
            val originalMime = readMime(original)
            if (patch == null) {
                ensure(originalMime == "image/jpeg" || originalMime == "image/png", FailureCode.OVERWRITE_STRIP_JPEG_PNG_ONLY)
            } else ensure(originalMime == "image/jpeg", FailureCode.OVERWRITE_EDIT_JPEG_ONLY)
            copyUriToFile(original, originalTemp)
            // External backup must be complete and independently readable before touching the original.
            writeDocument(backup, originalTemp, allowNewFileFallback = true)
            if (!verifySameFile(originalTemp, backup)) throw AppFailure(FailureCode.BACKUP_UNVERIFIED)
            val result = if (patch == null) renderClean(original, prepared) else {
                prepareEdited(original, prepared, patch)
                Prepared("image/jpeg", false)
            }
            if (patch == null && !verifyNoMetadata(prepared, result.mime)) {
                throw AppFailure(FailureCode.CLEAN_CHECK_FAILED)
            }
            var mutationAttempted = false
            try {
                mutationAttempted = true
                writeDocument(original, prepared)
                if (!verifySameFile(prepared, original)) throw AppFailure(FailureCode.REPLACED_FILE_UNVERIFIED)
            } catch (e: Exception) {
                if (mutationAttempted) {
                    try {
                        writeDocument(original, originalTemp)
                        if (!verifySameFile(originalTemp, original)) throw AppFailure(FailureCode.RESTORE_UNVERIFIED)
                    } catch (restoreError: Exception) {
                        // Причина для пользователя — ошибка восстановления; исходная ошибка замены приложена.
                        throw AppFailure(FailureCode.REPLACE_FAILED_RESTORE_FAILED, cause = restoreError).also { it.addSuppressed(e) }
                    }
                }
                throw AppFailure(FailureCode.REPLACE_CANCELLED_RESTORED, cause = e)
            }
            result.asResult(prepared.length(), true)
        } finally { originalTemp.delete(); prepared.delete() }
    }

    override suspend fun stripBatch(
        sources: List<String>, destinationTree: String, progress: (BatchProgress) -> Unit,
    ): BatchProgress = withContext(Dispatchers.IO) {
        ensure(sources.size in 1..50, FailureCode.BATCH_SIZE)
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
                if (!verifyNoMetadata(stage, prepared.mime)) throw AppFailure(FailureCode.CLEAN_CHECK_FAILED)
                val ext = if (prepared.mime == "image/jpeg") "jpg" else "png"
                val name = uniqueName(label, ext, index)
                target = DocumentsContract.createDocument(resolver, root, prepared.mime, name)
                    ?: throw AppFailure(FailureCode.CANNOT_CREATE_IN_FOLDER)
                writeDocument(target, stage, allowNewFileFallback = true)
                if (!verifySameFile(stage, target)) throw AppFailure(FailureCode.SAVED_FILE_UNVERIFIED)
                report = report.copy(succeeded = report.succeeded + 1)
            } catch (e: CancellationException) {
                target?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                throw e
            } catch (e: Exception) {
                target?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                report = report.copy(
                    failed = report.failed + 1,
                    errors = (report.errors + BatchFileError(label.take(80), e)).takeLast(50),
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
        ensure(it.scheme == ContentResolver.SCHEME_CONTENT, FailureCode.INVALID_URI)
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
        throw AppFailure(FailureCode.FORMAT_UNCONFIRMED, listOf(contentType.orEmpty()))
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        resolver.openInputStream(uri)?.use { stream ->
            FileOutputStream(target).use { sink -> copyLimited(stream, sink) }
        } ?: throw AppFailure(FailureCode.CANNOT_READ_SOURCE)
    }

    private fun copyLimited(source: InputStream, target: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = source.read(buffer)
            if (n < 0) return
            if (n == 0) continue
            total += n
            if (total > MAX_SOURCE_BYTES) throw AppFailure(FailureCode.SOURCE_TOO_LARGE)
            target.write(buffer, 0, n)
        }
    }

    private fun prepareEdited(source: Uri, output: File, patch: MetadataPatch) {
        val sourceMime = readMime(source)
        ensure(sourceMime == "image/jpeg", FailureCode.EDIT_JPEG_ONLY)
        val original = createTemp("editable_original", ".jpg")
        try {
            copyUriToFile(source, original)
            // При удалении GPS убираем и миниатюру EXIF: у обрезанного фото она показывает исходный кадр.
            JpegSegments.removeContradictorySegments(original, output, dropExifThumbnail = patch.removeGps)
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
            if (patch.removeGps && check.latLong != null) throw AppFailure(FailureCode.GPS_NOT_REMOVED)
            if (patch.latitude != null && patch.longitude != null) {
                val pair = check.latLong ?: throw AppFailure(FailureCode.GPS_NOT_WRITTEN)
                if (kotlin.math.abs(pair[0] - patch.latitude) > .0001 || kotlin.math.abs(pair[1] - patch.longitude) > .0001) {
                    throw AppFailure(FailureCode.GPS_MISMATCH)
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
                    throw AppFailure(FailureCode.EXIF_FIELD_UNVERIFIED, listOf(tag))
                }
            }
            if (output.length() == 0L) throw AppFailure(FailureCode.EMPTY_RESULT)
            if (patch.removeGps) {
                val hidden = JpegSegments.inspect(output)
                if (hidden.hasHiddenPayload) throw AppFailure(FailureCode.HIDDEN_DATA_FOUND, hidden.parts().toList())
            }
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
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AppFailure(FailureCode.UNSUPPORTED_IMAGE)
            val orientation = runCatching {
                ExifInterface(raw.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            // Поворот по EXIF создаёт вторую полную копию растра: учитываем её в бюджете памяти.
            val copies = if (needsTransform(orientation)) 2 else 1
            val sample = DecodeBudget.sampleSize(
                bounds.outWidth, bounds.outHeight, MAX_PIXELS, copies, DecodeBudget.availableHeapBytes(),
            )
            val decoded = BitmapFactory.decodeFile(
                raw.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true },
            ) ?: throw AppFailure(FailureCode.DECODE_FAILED)
            // Android 14+: Ultra HDR фото декодируются с gain map, и Bitmap.compress пишет обратно
            // MPF + второй кадр. Очищенная копия должна быть обычным JPEG без вторичных изображений.
            val bitmap = withoutGainmap(decoded)
            var oriented = bitmap
            try {
                oriented = applyExifOrientation(bitmap, orientation)
                FileOutputStream(output).use { stream ->
                    val format = if (mime == "image/jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    if (!oriented.compress(format, 95, stream)) throw AppFailure(FailureCode.ENCODE_FAILED)
                    stream.fd.sync()
                }
                if (output.length() == 0L) throw AppFailure(FailureCode.EMPTY_RESULT)
            } finally {
                if (oriented !== bitmap) oriented.recycle()
                bitmap.recycle()
            }
            return Prepared(mime, sample > 1)
        } catch (outOfMemory: OutOfMemoryError) {
            throw AppFailure(FailureCode.OUT_OF_MEMORY, cause = outOfMemory)
        } finally { raw.delete() }
    }

    private fun withoutGainmap(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < 34 || !bitmap.hasGainmap()) return bitmap
        runCatching { bitmap.gainmap = null }
        if (!bitmap.hasGainmap()) return bitmap
        // Запасной путь: перерисовать пиксели в новый bitmap, у которого gain map нет.
        val copy = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(copy).drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return copy
    }

    /** Найденные скрытые части как имена [HiddenPart]: интерфейс переводит их в слова. */
    private fun JpegSegments.Findings.parts(): Array<String> = listOfNotNull(
        HiddenPart.XMP.takeIf { xmp }, HiddenPart.IPTC.takeIf { iptc }, HiddenPart.COMMENT.takeIf { comment },
        HiddenPart.MPF.takeIf { mpf }, HiddenPart.TRAILER.takeIf { trailer }, HiddenPart.EXIF_THUMBNAIL.takeIf { exifThumbnail },
    ).map { it.name }.toTypedArray()

    /** Ориентация, при которой [applyExifOrientation] создаёт второй растр (поворот или отражение). */
    private fun needsTransform(orientation: Int): Boolean =
        orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED

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

    private fun hasOnlyImagePngChunks(file: File): Boolean =
        BufferedInputStream(FileInputStream(file)).use(PngCleanCheck::isClean)

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
        if (!allowNewFileFallback) throw AppFailure(FailureCode.NO_SAFE_OVERWRITE)
        val stream = resolver.openOutputStream(uri, "wt") ?: throw AppFailure(FailureCode.CANNOT_OPEN_OUTPUT)
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
        // Буквы и цифры любого алфавита сохраняются, остальное заменяется на "_".
        val base = raw.substringBeforeLast('.', raw)
            .map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
            .joinToString("").take(55).ifBlank { "photo" }
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
