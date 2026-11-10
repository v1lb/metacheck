package com.example.metacheck.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.metacheck.data.parser.ImageFormatDetector
import com.example.metacheck.domain.export.CleanFormat
import com.example.metacheck.domain.model.ImageFormat
import java.io.File

/**
 * Временные очищенные копии для «Очистить и отправить». Лежат в cache/shared и открываются
 * получателю только через FileProvider с разовым правом чтения. Старше суток удаляются при запуске.
 */
class SharedCopies(context: Context) {
    private val app = context.applicationContext
    private val dir get() = File(app.cacheDir, DIR).apply { mkdirs() }

    /** Файл назначения: JPEG, WebP и HEIC остаются в своём формате, видео — MP4/MOV, остальное сохраняется в PNG (как CleanFormat). */
    fun newTarget(source: String): Target {
        val format = runCatching {
            app.contentResolver.openInputStream(Uri.parse(source))?.use { stream ->
                // read() может вернуть меньше байт, чем просили: дочитываем, иначе JPEG ушёл бы как «.png».
                val head = ByteArray(32)
                var n = 0
                while (n < head.size) { val r = stream.read(head, n, head.size - n); if (r < 0) break; n += r }
                ImageFormatDetector.detect(head, n)
            }
        }.getOrNull() ?: ImageFormat.UNKNOWN
        val output = CleanFormat.outputFor(format)
        val file = File(dir, "metacheck_${System.currentTimeMillis()}.${output.extension}")
        file.createNewFile()
        return Target(file, FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file), output.mimeType)
    }

    fun discard(target: Target) {
        target.file.delete()
    }

    fun cleanOld(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val limit = System.currentTimeMillis() - maxAgeMs
        File(app.cacheDir, DIR).listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }

    data class Target(val file: File, val uri: Uri, val mime: String)

    private companion object {
        const val DIR = "shared"
    }
}
