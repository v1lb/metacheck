package com.example.metacheck.data.video

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.metacheck.domain.AppFailure
import com.example.metacheck.domain.FailureCode
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/** Открытый для чтения файл видео. Закрывает дескрипторы и удаляет временную копию. */
class OpenedVideo internal constructor(
    val source: ByteSource,
    private val closeables: List<Closeable>,
    private val temp: File?,
) : Closeable {
    override fun close() {
        closeables.forEach { runCatching { it.close() } }
        temp?.delete()
    }
}

object VideoFiles {
    /**
     * Видео читается по месту через дескриптор (без копии в память). Если провайдер отдаёт только поток
     * без перемотки, файл копируется во временную папку приложения.
     */
    fun open(resolver: ContentResolver, uri: Uri, cacheDir: File): OpenedVideo {
        val pfd: ParcelFileDescriptor? = try { resolver.openFileDescriptor(uri, "r") } catch (e: SecurityException) { throw e } catch (_: Exception) { null }
        if (pfd != null) {
            if (pfd.statSize >= 0) {
                val stream = FileInputStream(pfd.fileDescriptor)
                try {
                    val source = ChannelByteSource(stream.channel)
                    return OpenedVideo(source, listOf(stream, pfd), null)
                } catch (_: IOException) {
                    stream.close()
                }
            }
            pfd.close()
        }
        val temp = File.createTempFile("meta_video_", ".bin", cacheDir)
        try {
            val input = resolver.openInputStream(uri) ?: throw AppFailure(FailureCode.CANNOT_READ_SOURCE)
            input.use { s -> FileOutputStream(temp).use { s.copyTo(it, 1 shl 20) } }
            val file = RandomAccessFile(temp, "r")
            return OpenedVideo(ChannelByteSource(file.channel), listOf(file), temp)
        } catch (e: Exception) {
            temp.delete()
            if (e is IOException && e !is AppFailure && temp.parentFile?.usableSpace?.let { it < 32L * 1024 * 1024 } == true) {
                throw AppFailure(FailureCode.NOT_ENOUGH_SPACE, cause = e)
            }
            throw e
        }
    }

    /** Первые байты файла для определения формата. */
    fun head(resolver: ContentResolver, uri: Uri, size: Int = 32): ByteArray {
        val buf = ByteArray(size)
        var n = 0
        resolver.openInputStream(uri)?.use { s ->
            while (n < size) { val r = s.read(buf, n, size - n); if (r < 0) break; n += r }
        } ?: throw AppFailure(FailureCode.CANNOT_READ_SOURCE)
        return buf.copyOf(n)
    }
}
