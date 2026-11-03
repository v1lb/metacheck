package com.example.metacheck.data.history

import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.export.ReportJson
import com.example.metacheck.domain.AppFailure
import com.example.metacheck.domain.FailureCode
import com.example.metacheck.domain.model.HistoryEntry
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.repository.HistoryRepository
import com.example.metacheck.domain.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * История хранится в приватной папке приложения (filesDir), каждый отчёт отдельным зашифрованным файлом.
 * Копии самих фотографий не сохраняются никогда.
 * Идентификатор проверяется по строгому шаблону, поэтому через него нельзя выйти из папки (path traversal).
 *
 * Лимиты (аудит, п. 18). На диске всегда хранится до [STORAGE_LIMIT] отчётов, а тариф влияет
 * только на то, сколько из них показывается ([visibleLimit]). Раньше лимит тарифа применялся при
 * сохранении и удалял файлы: в первые секунды после запуска статус Pro ещё не прочитан (Free),
 * и отчёт, сохранённый в этот момент, безвозвратно стирал 40 отчётов платящего пользователя.
 * То же случалось после возврата денег, смены аккаунта Google Play или сбоя сверки с Play.
 * «Очистить историю» удаляет все файлы, в том числе скрытые.
 */
class FileHistoryRepository(
    private val dir: File,
    private val crypto: Crypto,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Сколько последних отчётов показывать. Читается при каждом [list]: зависит от тарифа. */
    private val visibleLimit: suspend () -> Int = { STORAGE_LIMIT },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HistoryRepository {

    private val lock = Mutex()
    private val idRegex = Regex("^\\d{13}_[0-9a-f]{32}$")

    override suspend fun save(report: MetaReport): Result<String> = withContext(io) {
        lock.withLock {
            runCatchingCancellable {
                if (!dir.exists() && !dir.mkdirs()) throw AppFailure(FailureCode.HISTORY_UNAVAILABLE)
                val id = clock().toString().padStart(13, '0') + "_" + UUID.randomUUID().toString().replace("-", "")
                val bytes = crypto.encrypt(ReportJson.toJson(report).toString().toByteArray(Charsets.UTF_8))
                val tmp = File(dir, "$id.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(File(dir, "$id.rep"))) {
                    tmp.delete()
                    throw AppFailure(FailureCode.HISTORY_UNAVAILABLE)
                }
                // Только физический предел хранилища, не тариф: см. описание класса.
                reportFiles().drop(STORAGE_LIMIT).forEach { it.delete() }
                id
            }
        }
    }

    override suspend fun list(): List<HistoryEntry> = withContext(io) {
        val limit = visibleLimit().coerceIn(0, STORAGE_LIMIT)
        // Расшифровываем только показываемые отчёты; повреждённые пропускаем и берём следующие.
        reportFiles().asSequence().mapNotNull { file ->
            val report = readReport(file) ?: return@mapNotNull null
            val id = file.name.removeSuffix(".rep")
            HistoryEntry(
                id = id,
                createdAt = id.substringBefore('_').toLong(),
                fileName = report.file.name,
                format = report.file.format,
                hasAiEvidence = report.hasAiEvidence,
                hasGps = report.gps != null,
            )
        }.take(limit).toList()
    }

    /** Открывается любой хранимый отчёт: id приходит только из [list], то есть из показанных. */
    override suspend fun load(id: String): MetaReport? = withContext(io) {
        if (!idRegex.matches(id)) return@withContext null
        val file = File(dir, "$id.rep")
        if (file.isFile) readReport(file) else null
    }

    override suspend fun delete(id: String) {
        withContext(io) { lock.withLock { deleteBlocking(id) } }
    }

    /** Файл, который не удалось удалить, — ошибка: иначе интерфейс сообщил бы об удалении, а отчёт остался. */
    private fun deleteBlocking(id: String) {
        if (!idRegex.matches(id)) return
        val file = File(dir, "$id.rep")
        if (!file.delete() && file.exists()) throw AppFailure(FailureCode.HISTORY_UNAVAILABLE)
    }

    override suspend fun clear() {
        withContext(io) {
            lock.withLock {
                dir.listFiles()?.forEach { if (it.name.endsWith(".rep") || it.name.endsWith(".tmp")) it.delete() }
                // «История удалена» показываем, только если файлов действительно не осталось.
                if (dir.listFiles { f -> f.name.endsWith(".rep") }?.isNotEmpty() == true) {
                    throw AppFailure(FailureCode.HISTORY_UNAVAILABLE)
                }
            }
        }
    }

    /** Новые сверху: имя начинается с 13-значного времени, поэтому сортировка по имени = по времени. */
    private fun reportFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".rep") && idRegex.matches(f.name.removeSuffix(".rep")) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    private fun readReport(file: File): MetaReport? =
        try {
            ReportJson.fromJson(String(crypto.decrypt(file.readBytes()), Charsets.UTF_8))
        } catch (e: Exception) {
            null // повреждённый файл или ключ Keystore больше недоступен
        }

    companion object {
        /** Сколько отчётов хранится на диске при любом тарифе. Равен лимиту Pro. */
        const val STORAGE_LIMIT = 50
    }
}
