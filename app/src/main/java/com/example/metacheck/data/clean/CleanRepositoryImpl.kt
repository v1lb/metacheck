package com.example.metacheck.data.clean

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.DocumentsContract
import androidx.exifinterface.media.ExifInterface
import com.example.metacheck.domain.export.CleanFormat
import com.example.metacheck.domain.model.CleanResult
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.TooLargeException
import com.example.metacheck.domain.model.UnsupportedFormatException
import com.example.metacheck.domain.repository.CleanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Очистка метаданных пересохранением: картинка декодируется в пиксели и кодируется заново,
 * поэтому EXIF, XMP, GPS, ICC-профиль и текстовые блоки PNG не переносятся в копию.
 * Исходный файл не меняется. У JPEG при этом есть небольшая потеря качества (q=95).
 */
class CleanRepositoryImpl(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CleanRepository {

    override suspend fun clean(source: String, target: String, format: ImageFormat): Result<CleanResult> =
        withContext(io) {
            val dst = Uri.parse(target)
            try {
                Result.success(cleanBlocking(Uri.parse(source), dst, format))
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                runCatching { DocumentsContract.deleteDocument(resolver, dst) }
                Result.failure(TooLargeException())
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, dst) }
                Result.failure(e)
            }
        }

    private fun cleanBlocking(src: Uri, dst: Uri, format: ImageFormat): CleanResult {
        require(src.scheme == ContentResolver.SCHEME_CONTENT && dst.scheme == ContentResolver.SCHEME_CONTENT) {
            "Недопустимый источник или цель"
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Не удалось открыть файл")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnsupportedFormatException()

        var sample = 1
        while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > MAX_PIXELS) sample *= 2

        val orientation = resolver.openInputStream(src)?.use {
            try {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } ?: ExifInterface.ORIENTATION_NORMAL

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw UnsupportedFormatException()
        val oriented = applyOrientation(decoded, orientation)

        try {
            val output = CleanFormat.outputFor(format)
            val compress = if (output.extension == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val ok = resolver.openOutputStream(dst, "wt")?.use { oriented.compress(compress, JPEG_QUALITY, it) }
                ?: throw IOException("Не удалось открыть файл для записи")
            if (!ok) throw IOException("Не удалось закодировать изображение")
        } finally {
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
        return CleanResult(downscaled = sample > 1)
    }

    /** EXIF-ориентация удаляется вместе с метаданными, поэтому поворачиваем пиксели заранее. */
    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private companion object {
        /** До 16 Мп без уменьшения; больше уменьшаем вдвое, пока не влезет. */
        const val MAX_PIXELS = 16_000_000L
        const val JPEG_QUALITY = 95
    }
}
