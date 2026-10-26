package com.example.metacheck.data.history

import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.export.ReportJson
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
import java.io.IOException
import java.util.UUID

/**
 * История хранится в приватной папке приложения (filesDir), каждый отчёт отдельным зашифрованным файлом.
 * Копии самих фотографий не сохраняются никогда.
 * Идентификатор проверяется по строгому шаблону, поэтому через него нельзя выйти из папки (path traversal).
 */
class FileHistoryRepository(
    private val dir: File,
    private val crypto: Crypto,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Лимит читается при каждом сохранении: в Pro история длиннее. */
    private val maxEntries: () -> Int = { 50 },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HistoryRepository {

    private val lock = Mutex()
    private val idRegex = Regex("^\\d{13}_[0-9a-f]{32}$")

    override suspend fun save(report: MetaReport): Result<String> = withContext(io) {
        lock.withLock {
            runCatchingCancellable {
                if (!dir.exists() && !dir.mkdirs()) throw IOException("Не удалось создать папку истории")
                val id = clock().toString().padStart(13, '0') + "_" + UUID.randomUUID().toString().replace("-", "")
                val bytes = crypto.encrypt(ReportJson.toJson(report).toString().toByteArray(Charsets.UTF_8))
                val tmp = File(dir, "$id.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(File(dir, "$id.rep"))) {
                    tmp.delete()
                    throw IOException("Не удалось сохранить отчёт")
                }
                reportFiles().drop(maxEntries()).forEach { it.delete() }
                id
            }
        }
    }

    override suspend fun list(): List<HistoryEntry> = withContext(io) {
        reportFiles().mapNotNull { file ->
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
        }
    }

    override suspend fun load(id: String): MetaReport? = withContext(io) {
        if (!idRegex.matches(id)) return@withContext null
        val file = File(dir, "$id.rep")
        if (file.isFile) readReport(file) else null
    }

    override suspend fun delete(id: String) {
        withContext(io) { deleteBlocking(id) }
    }

    private fun deleteBlocking(id: String) {
        if (idRegex.matches(id)) File(dir, "$id.rep").delete()
    }

    override suspend fun clear() {
        withContext(io) {
            lock.withLock {
                dir.listFiles()?.forEach { if (it.name.endsWith(".rep") || it.name.endsWith(".tmp")) it.delete() }
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
}
