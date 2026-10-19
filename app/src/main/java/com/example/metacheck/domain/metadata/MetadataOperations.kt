package com.example.metacheck.domain.metadata

import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.runCatchingCancellable
import com.example.metacheck.domain.repository.EntitlementRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
}

/** Persistent UTC-day quota for successful single-file privacy writes. */
interface DailyCleanQuota {
    fun usedToday(): Int
    fun recordSuccess(): Boolean
}

class DailyLimitReachedException : Exception("Бесплатная очистка: 1 фотография за день. Повторите завтра (UTC) или откройте Pro")

/** Serialize quota check + successful write so concurrent exports cannot bypass Free limits. */
class MetadataStudioUseCase(
    private val repository: MetadataRewriteRepository,
    private val entitlements: EntitlementRepository,
    private val quota: DailyCleanQuota,
) {
    private val lock = Mutex()
    fun usedToday(): Int = quota.usedToday()
    private suspend fun limitedWrite(action: suspend () -> MetadataWriteResult): Result<MetadataWriteResult> =
        runCatchingCancellable {
            lock.withLock {
                val free = !entitlements.entitlement.value.isPro
                if (free && quota.usedToday() >= 1) {
                    throw DailyLimitReachedException()
                }
                val result = action()
                if (free && !quota.recordSuccess()) {
                    throw IllegalStateException("Файл создан, но не удалось сохранить счётчик лимита. Проверьте результат в выбранной папке")
                }
                result
            }
        }

    suspend fun strip(source: String, destination: String): Result<MetadataWriteResult> = limitedWrite {
        repository.stripCopy(source, destination)
    }

    suspend fun wipeGps(source: String, destination: String): Result<MetadataWriteResult> = limitedWrite {
        repository.wipeGpsCopy(source, destination)
    }

    suspend fun edit(source: String, destination: String, patch: MetadataPatch): Result<MetadataWriteResult> = runCatchingCancellable {
        if (!entitlements.entitlement.value.isPro) throw ProRequiredException()
        val errors = MetadataPatchValidator.validate(patch)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        repository.editCopy(source, destination, patch)
    }

    suspend fun overwrite(source: String, backup: String, patch: MetadataPatch?): Result<MetadataWriteResult> = runCatchingCancellable {
        if (patch != null) {
            if (!entitlements.entitlement.value.isPro) throw ProRequiredException()
            val errors = MetadataPatchValidator.validate(patch)
            require(errors.isEmpty()) { errors.joinToString("; ") }
            repository.overwriteWithBackup(source, backup, patch)
        } else {
            lock.withLock {
                val free = !entitlements.entitlement.value.isPro
                if (free && quota.usedToday() >= 1) throw DailyLimitReachedException()
                val result = repository.overwriteWithBackup(source, backup, null)
                if (free && !quota.recordSuccess()) {
                    throw IllegalStateException("Оригинал заменён, но лимит не сохранён: проверьте файл и резервную копию")
                }
                result
            }
        }
    }

    suspend fun batch(sources: List<String>, tree: String, progress: (BatchProgress) -> Unit): Result<BatchProgress> = runCatchingCancellable {
        if (!entitlements.entitlement.value.isPro) throw ProRequiredException()
        require(sources.isNotEmpty()) { "Не выбраны изображения" }
        require(sources.size <= 50) { "За одну операцию допускается до 50 файлов" }
        repository.stripBatch(sources, tree, progress)
    }
}
