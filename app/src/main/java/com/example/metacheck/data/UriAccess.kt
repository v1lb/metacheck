package com.example.metacheck.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Права на файлы, выбранные через системный диалог (аудит, п. 14).
 *
 * Права от OpenDocument/CreateDocument временные: живут, пока жив процесс. Приложение сохраняло URI
 * в rememberSaveable/SavedStateHandle, и после выгрузки процесса системой операция падала с
 * SecurityException. Теперь:
 *  - файл, выбранный для студии, получает постоянное право (takePersistableUriPermission);
 *  - число постоянных прав ограничено [keep]: старые отпускаются (Android даёт 128–512 на приложение,
 *    и держать доступ к старым фото пользователя без нужды не стоит);
 *  - перед использованием восстановленного URI доступ проверяется ([canRead]).
 */
class UriAccess(private val resolver: ContentResolver, private val keep: Int = DEFAULT_KEEP) {

    /** Пытается сохранить доступ после перезапуска. Для URI без такой возможности (Share, камера) ничего не делает. */
    fun persist(uri: Uri) {
        val granted = tryTake(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ||
            tryTake(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (granted) trim()
    }

    suspend fun canRead(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readable(uris: List<String>): List<String> = uris.filter { canRead(it) }

    private fun tryTake(uri: Uri, flags: Int): Boolean = try {
        resolver.takePersistableUriPermission(uri, flags)
        true
    } catch (_: SecurityException) {
        false // провайдер не выдал постоянное право: остаётся временное
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun trim() {
        val persisted = runCatching { resolver.persistedUriPermissions }.getOrNull() ?: return
        val release = PersistedGrants.toRelease(persisted.map { it.uri.toString() to it.persistedTime }, keep)
        for (p in persisted) {
            if (p.uri.toString() !in release) continue
            var flags = 0
            if (p.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (p.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { resolver.releasePersistableUriPermission(p.uri, flags) }
        }
    }

    companion object {
        const val DEFAULT_KEEP = 32
    }
}

/** Чистая логика выбора прав для освобождения: самые старые сверх лимита. */
internal object PersistedGrants {
    fun toRelease(grants: List<Pair<String, Long>>, keep: Int): Set<String> =
        grants.sortedByDescending { it.second }.drop(keep.coerceAtLeast(0)).map { it.first }.toSet()
}
