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

    /** Возвращает в [original] содержимое резервной копии [backup], сделанной перед перезаписью. */
    suspend fun restoreFromBackup(original: String, backup: String): MetadataWriteResult =
        throw UnsupportedOperationException("restore")

    /** Сдвиг дат съёмки у нескольких JPEG, копии — в папку [destinationTree]. */
    suspend fun shiftDatesBatch(
        sources: List<String>,
        destinationTree: String,
        shiftSeconds: Long,
        progress: (BatchProgress) -> Unit,
    ): BatchProgress = throw UnsupportedOperationException("shift")

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

    /** Отмена перезаписи оригинала. Без лимита и без Pro: вернуть свой файл можно всегда. */
    suspend fun undoOverwrite(original: String, backup: String): Result<MetadataWriteResult> = runCatchingCancellable {
        lock.withLock { repository.restoreFromBackup(original, backup) }
    }

    /** Сдвиг даты съёмки у нескольких фото (Pro): часовой пояс камеры был выставлен неверно. */
    suspend fun shiftDates(sources: List<String>, tree: String, shiftSeconds: Long, progress: (BatchProgress) -> Unit): Result<BatchProgress> =
        runCatchingCancellable {
            lock.withLock {
                if (!entitlements.current().isPro) throw ProRequiredException()
                ensure(sources.size in 1..BatchLimits.PRO_PER_RUN, FailureCode.BATCH_SIZE)
                ensure(shiftSeconds != 0L && kotlin.math.abs(shiftSeconds) <= DateShift.MAX_SECONDS, FailureCode.BATCH_SIZE)
                repository.shiftDatesBatch(sources, tree, shiftSeconds, progress)
            }
        }

    /**
     * Пакетная очистка. Бесплатно — до [BatchLimits.FREE_PER_RUN] фото за раз, каждое успешное фото
     * входит в дневной лимит. Pro — до [BatchLimits.PRO_PER_RUN] за раз без лимита.
     */
    suspend fun batch(sources: List<String>, tree: String, progress: (BatchProgress) -> Unit): Result<BatchProgress> = runCatchingCancellable {
        lock.withLock {
            val free = !entitlements.current().isPro
            if (free) {
                if (sources.size > BatchLimits.FREE_PER_RUN) throw ProRequiredException()
                if (usedToday() + sources.size > DailyCleanQuota.FREE_DAILY_LIMIT) throw DailyLimitReachedException()
            }
            ensure(sources.size in 1..BatchLimits.PRO_PER_RUN, FailureCode.BATCH_SIZE)
            val result = repository.stripBatch(sources, tree, progress)
            // Файлы уже сохранены: сбой счётчика не должен выглядеть как неудачная очистка.
            if (free) withContext(io) { runCatching { repeat(result.succeeded) { quota.recordSuccess() } } }
            result
        }
    }
}
