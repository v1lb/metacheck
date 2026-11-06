package com.example.metacheck.domain.metadata

import com.example.metacheck.domain.FailureCode
import com.example.metacheck.domain.ensure
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.runCatchingCancellable
import com.example.metacheck.domain.repository.EntitlementRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Domain boundary. Android content URIs remain opaque Strings outside the data layer. */
interface MetadataRewriteRepository {
    suspend fun stripCopy(source: String, destination: String): MetadataWriteResult
    suspend fun wipeGpsCopy(source: String, destination: String): MetadataWriteResult
    suspend fun editCopy(source: String, destination: String, patch: MetadataPatch): MetadataWriteResult
    suspend fun overwriteWithBackup(
        source: String,
        backupDestination: String,
        patch: MetadataPatch?,
    ): MetadataWriteResult
    suspend fun stripBatch(
        sources: List<String>,
        destinationTree: String,
        progress: (BatchProgress) -> Unit,
    ): BatchProgress

    /** true: источник — видео MP4/MOV (по сигнатуре файла, не по MIME). */
    suspend fun isVideo(source: String): Boolean = false

    /** Очищенная копия видео без пережатия. */
    suspend fun stripVideoCopy(source: String, destination: String): MetadataWriteResult =
        throw UnsupportedOperationException("video")
}

/**
 * Persistent UTC-day quota for successful single-file privacy writes.
 * Это UX-ограничение бесплатного тарифа, а не защита: «Очистить данные» всё равно его обнуляет.
 * Поэтому при любой ошибке хранилища квота работает в пользу пользователя (fail-open),
 * а не блокирует бесплатный тариф навсегда.
 */
interface DailyCleanQuota {
    fun usedToday(): Int

    /** false: счётчик не удалось сохранить на диск. Операцию пользователя это не отменяет. */
    fun recordSuccess(): Boolean

    companion object {
        const val FREE_DAILY_LIMIT = 10
    }
}

class DailyLimitReachedException : Exception("Free daily limit reached")

/** Serialize quota check + successful write so concurrent exports cannot bypass Free limits. */
class MetadataStudioUseCase(
    private val repository: MetadataRewriteRepository,
    private val entitlements: EntitlementRepository,
    private val quota: DailyCleanQuota,
    /** Счётчик квоты читает Keystore и диск: никогда не в главном потоке. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Mutex()
    suspend fun usedToday(): Int = withContext(io) { quota.usedToday() }
    private suspend fun limitedWrite(action: suspend () -> MetadataWriteResult): Result<MetadataWriteResult> =
        runCatchingCancellable {
            lock.withLock {
                val free = !entitlements.current().isPro
                if (free && usedToday() >= DailyCleanQuota.FREE_DAILY_LIMIT) {
                    throw DailyLimitReachedException()
                }
                val result = action()
                // Файл уже записан и проверен. Сбой счётчика не повод сообщать пользователю об ошибке:
                // раньше при сломанном Keystore каждая успешная очистка выглядела как неудачная.
                if (free) withContext(io) { quota.recordSuccess() }
                result
            }
        }

    suspend fun strip(source: String, destination: String): Result<MetadataWriteResult> =
        if (isVideo(source)) stripVideo(source, destination) else limitedWrite { repository.stripCopy(source, destination) }

    /** У видео отдельного удаления GPS нет: очистка убирает и место съёмки, и остальные теги. */
    suspend fun wipeGps(source: String, destination: String): Result<MetadataWriteResult> =
        if (isVideo(source)) stripVideo(source, destination) else limitedWrite { repository.wipeGpsCopy(source, destination) }

    private suspend fun isVideo(source: String): Boolean =
        try { repository.isVideo(source) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }

    /** Очистка видео — функция Pro; в дневной лимит бесплатных очисток фото не входит. */
    private suspend fun stripVideo(source: String, destination: String): Result<MetadataWriteResult> = runCatchingCancellable {
        lock.withLock {
            if (!entitlements.current().isPro) throw ProRequiredException()
            repository.stripVideoCopy(source, destination)
        }
    }

    suspend fun edit(source: String, destination: String, patch: MetadataPatch): Result<MetadataWriteResult> = limitedWrite {
        val issues = MetadataPatchValidator.validate(patch)
        if (issues.isNotEmpty()) throw InvalidPatchException(issues)
        repository.editCopy(source, destination, patch)
    }

    suspend fun overwrite(source: String, backup: String, patch: MetadataPatch?): Result<MetadataWriteResult> = limitedWrite {
        if (patch != null) {
            val issues = MetadataPatchValidator.validate(patch)
            if (issues.isNotEmpty()) throw InvalidPatchException(issues)
        }
        repository.overwriteWithBackup(source, backup, patch)
    }

    suspend fun batch(sources: List<String>, tree: String, progress: (BatchProgress) -> Unit): Result<BatchProgress> = runCatchingCancellable {
        if (!entitlements.current().isPro) throw ProRequiredException()
        ensure(sources.size in 1..50, FailureCode.BATCH_SIZE)
        repository.stripBatch(sources, tree, progress)
    }
}
