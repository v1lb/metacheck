package com.example.metacheck

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import java.io.File

class MetaCheckApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        enableStrictModeInDebug()
        warmUpPreferences()
        cleanOldCameraShots()
        cleanStaleMetadataTemps()
        // Очищенные копии для «Отправить без метаданных»: получатель мог читать файл позже, держим сутки.
        // Отдельный экземпляр, а не container: иначе весь AppContainer (Keystore, Billing) создавался бы в этом потоке.
        Thread { runCatching { com.example.metacheck.data.share.SharedCopies(this).cleanOld() } }.start()
    }

    /**
     * getSharedPreferences() сразу запускает чтение файла в фоновом потоке. Первое getX() из главного
     * потока ждёт окончания этого чтения. Открываем файлы здесь, пока система создаёт Activity,
     * чтобы к моменту создания AppContainer они уже были в памяти.
     */
    private fun warmUpPreferences() {
        for (name in listOf("settings", "entitlement", "quota_secure_v2")) getSharedPreferences(name, MODE_PRIVATE)
    }

    /** Только в debug: пишет в Logcat (тег StrictMode) любые обращения к диску и сети из главного потока. */
    private fun enableStrictModeInDebug() {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build(),
        )
    }

    /**
     * Снимки с камеры лежат во временной папке. Удаляем старше часа: свежий файл мог только что
     * вернуться из камеры, если процесс успели убить.
     */
    private fun cleanStaleMetadataTemps() {
        Thread {
            val limit = System.currentTimeMillis() - 60 * 60 * 1000L
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("meta_") && file.lastModified() < limit) {
                    file.delete()
                }
            }
        }.start()
    }

    private fun cleanOldCameraShots() {
        Thread {
            val limit = System.currentTimeMillis() - 60 * 60 * 1000L
            File(cacheDir, "camera").listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
        }.start()
    }
}
