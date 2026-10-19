package com.example.metacheck

import android.app.Application
import java.io.File

class MetaCheckApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        cleanOldCameraShots()
    }

    /**
     * Снимки с камеры лежат во временной папке. Удаляем старше часа: свежий файл мог только что
     * вернуться из камеры, если процесс успели убить.
     */
    private fun cleanOldCameraShots() {
        Thread {
            val limit = System.currentTimeMillis() - 60 * 60 * 1000L
            File(cacheDir, "camera").listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
        }.start()
    }
}
