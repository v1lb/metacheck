package com.example.metacheck.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Временные очищенные копии для «Очистить и отправить». Лежат в cache/shared и открываются
 * получателю только через FileProvider с разовым правом чтения. Старше суток удаляются при запуске.
 */
class SharedCopies(context: Context) {
    private val app = context.applicationContext
    private val dir get() = File(app.cacheDir, DIR).apply { mkdirs() }

    /** Файл назначения: JPEG остаётся JPEG, остальное очистка сохраняет в PNG (как CleanFormat). */
    fun newTarget(source: String): Target {
        val jpeg = runCatching {
            app.contentResolver.openInputStream(Uri.parse(source))?.use { stream ->
                // read() может вернуть меньше байт, чем просили: дочитываем, иначе JPEG ушёл бы как «.png».
                val head = ByteArray(3)
                var n = 0
                while (n < head.size) { val r = stream.read(head, n, head.size - n); if (r < 0) break; n += r }
                n == 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
            } ?: false
        }.getOrDefault(false)
        val file = File(dir, "metacheck_${System.currentTimeMillis()}.${if (jpeg) "jpg" else "png"}")
        file.createNewFile()
        return Target(file, FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file), if (jpeg) "image/jpeg" else "image/png")
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
