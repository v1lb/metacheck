package com.example.metacheck.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.metacheck.MetaCheckApplication
import com.example.metacheck.R
import com.example.metacheck.ui.text.ErrorTexts
import com.example.metacheck.ui.text.UiText
import com.example.metacheck.data.UriAccess
import com.example.metacheck.domain.metadata.BatchProgress
import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataStudioUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Operations survive ordinary Compose recomposition; work is on Dispatchers.IO in repository. */
class MetadataStudioViewModel(
    private val studio: MetadataStudioUseCase,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val uriAccess: UriAccess? = null,
    private val sharedCopies: com.example.metacheck.data.share.SharedCopies? = null,
    private val folderAudit: com.example.metacheck.domain.metadata.FolderAuditRepository? = null,
) : ViewModel() {
    /** Проверка папки: сколько фото уже прочитано и итог. */
    data class AuditState(
        val running: Boolean = false,
        val scanned: Int = 0,
        val result: com.example.metacheck.domain.metadata.AuditResult? = null,
    )
    private val _audit = MutableStateFlow(AuditState())
    val audit = _audit.asStateFlow()

    /** Проверка всей папки. По готовности [onDone] получает фото для очистки, самые рискованные первыми. */
    fun auditFolder(tree: Uri, onDone: (List<String>) -> Unit) {
        val repo = folderAudit ?: return
        if (_audit.value.running) return
        _audit.value = AuditState(running = true)
        viewModelScope.launch {
            try {
                val result = repo.scan(tree.toString()) { n -> _audit.value = _audit.value.copy(scanned = n) }
                _audit.value = AuditState(running = false, scanned = result.scanned, result = result)
                onDone(result.findings.map { it.uri })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _audit.value = AuditState()
                _message.value = UiText.Res(R.string.studio_audit_failed)
            }
        }
    }

    /** Файл выбран пользователем: сохраняем доступ, чтобы он пережил выгрузку процесса. */
    fun persistAccess(uri: Uri) {
        uriAccess?.persist(uri)
    }

    /** true, если к восстановленному после перезапуска URI ещё есть доступ. */
    suspend fun canRead(uri: String): Boolean = uriAccess?.canRead(uri) ?: true

    suspend fun readable(uris: List<String>): List<String> = uriAccess?.readable(uris) ?: uris

    private val pendingWrites = PendingWrites(object : StateStore {
        override fun get(key: String): Any? = savedState[key]
        override fun set(key: String, value: Any?) { savedState[key] = value }
    })

    /** Ожидающая ответа SAF операция. Переживает пересоздание Activity и смерть процесса. */
    val pending: StateFlow<PendingWrite?> = savedState.getStateFlow(PendingWrites.KEY, null)

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message = _message.asStateFlow()
    private val _batch = MutableStateFlow(BatchProgress())
    val batch = _batch.asStateFlow()
    /** Счётчик читается в фоне (Keystore): в конструкторе ViewModel, т. е. в главном потоке, его не трогаем. */
    val usedToday = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            usedToday.value = try { studio.usedToday() } catch (e: CancellationException) { throw e } catch (_: Exception) { 0 }
        }
    }

    fun clearMessage() { _message.value = null }

    /** Готовая к отправке очищенная копия: экран открывает системное окно «Поделиться» и сбрасывает значение. */
    private val _shareReady = MutableStateFlow<com.example.metacheck.data.share.SharedCopies.Target?>(null)
    val shareReady = _shareReady.asStateFlow()

    fun consumeShare() { _shareReady.value = null }

    /**
     * «Очистить и отправить»: очищенная копия во временную папку и сразу окно «Поделиться».
     * Отправляется только проверенная копия: непроверенную удаляем, чтобы GPS случайно не ушёл.
     */
    fun cleanAndShare(source: String) {
        val copies = sharedCopies ?: return
        work {
            val target = withContext(Dispatchers.IO) { copies.newTarget(source) }
            studio.strip(source, target.uri.toString()).fold(
                onSuccess = {
                    if (it.verified) { _shareReady.value = target; AppPrompts.cleanSucceeded() }
                    else { copies.discard(target); _message.value = UiText.Res(R.string.share_not_verified) }
                },
                onFailure = { copies.discard(target); _message.value = failMessage(it) },
            )
            usedToday.value = studio.usedToday()
        }
    }
    fun stripCopy(
        source: String,
        destination: Uri,
        onSavedCopy: (() -> Unit)? = null
    ) = work {
        studio.strip(source, destination.toString()).fold(
            onSuccess = {
                _message.value = successMessage(R.string.studio_msg_clean_saved, it.verified, it.downscaled)
                if (it.verified) onSavedCopy?.invoke()
            },
            onFailure = { _message.value = failMessage(it) },
        )
        usedToday.value = studio.usedToday()
    }

    fun wipeGpsCopy(source: String, destination: Uri) = work {
        studio.wipeGps(source, destination.toString()).fold(
            onSuccess = {
                _message.value = successMessage(
                    R.string.studio_msg_gps_removed, it.verified, it.downscaled,
                    extra = if (it.verified) UiText.Res(R.string.studio_msg_share_copy) else null,
                )
            },
            onFailure = { _message.value = failMessage(it) },
        )
        usedToday.value = studio.usedToday()
    }

    fun requestEditCopy(source: String, patch: MetadataPatch) = pendingWrites.requestEditCopy(source, patch)

    fun requestOverwrite(source: String, op: OverwriteOp) = pendingWrites.requestOverwrite(source, op)

    /** true: можно открывать диалог выбора места для резервной копии. */
    fun confirmOverwrite(): Boolean = pendingWrites.confirmOverwrite()

    fun cancelPending() = pendingWrites.cancel()

    /** Результат CreateDocument для «Сохранить изменённую копию». */
    fun onEditCopyDestination(destination: Uri?) {
        val op = pendingWrites.takeEditCopy()
        if (destination == null) return
        if (op == null) { _message.value = INTERRUPTED; return }
        editCopy(op.source, destination, op.patch)
    }

    /** Результат CreateDocument для резервной копии перед перезаписью оригинала. */
    fun onBackupDestination(destination: Uri?) {
        val op = pendingWrites.takeConfirmedOverwrite()
        if (destination == null) return
        if (op == null) { _message.value = INTERRUPTED; return }
        val patch = when (val mode = op.op) {
            OverwriteOp.Strip -> null
            is OverwriteOp.Edit -> mode.patch
        }
        overwrite(op.source, destination, patch)
    }

    private fun editCopy(source: String, destination: Uri, patch: MetadataPatch) = work {
        studio.edit(source, destination.toString(), patch).fold(
            onSuccess = { _message.value = successMessage(R.string.studio_msg_exif_saved, it.verified, false) },
            onFailure = { _message.value = failMessage(it) },
        )
    }

    private fun overwrite(source: String, backup: Uri, patch: MetadataPatch?) = work {
        studio.overwrite(source, backup.toString(), patch).fold(
            onSuccess = { _message.value = successMessage(R.string.studio_msg_original_replaced, it.verified, it.downscaled) },
            onFailure = { _message.value = failMessage(it) },
        )
        usedToday.value = studio.usedToday()
    }

    fun batchStrip(sources: List<String>, targetTree: Uri, onDone: () -> Unit = {}) = work {
        _batch.value = BatchProgress(total = sources.size, running = true)
        studio.batch(sources, targetTree.toString()) { _batch.value = it }.fold(
            onSuccess = {
                _message.value = UiText.Res(R.string.studio_msg_batch_done, it.succeeded, it.failed)
                _batch.value = it
                onDone()
            },
            onFailure = {
                _batch.value = _batch.value.copy(running = false)
                _message.value = failMessage(it)
            },
        )
        usedToday.value = studio.usedToday()
    }

    private fun work(operation: suspend () -> Unit) {
        // Не молча: к этому моменту системный диалог уже мог создать пустой файл под новую операцию.
        if (_busy.value) { _message.value = UiText.Res(R.string.main_msg_busy); return }
        viewModelScope.launch {
            _busy.value = true
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = failMessage(e) }
            finally { _busy.value = false }
        }
    }

    /** Сообщение собирается из предложений-ресурсов: так оно переводится целиком, без склейки строк. */
    private fun successMessage(
        @androidx.annotation.StringRes title: Int,
        verified: Boolean,
        downscaled: Boolean,
        extra: UiText? = null,
    ): UiText {
        if (verified) AppPrompts.cleanSucceeded()
        return successMessageText(title, verified, downscaled, extra)
    }

    private fun successMessageText(
        @androidx.annotation.StringRes title: Int,
        verified: Boolean,
        downscaled: Boolean,
        extra: UiText?,
    ): UiText = UiText.Joined(
        listOfNotNull(
            UiText.Res(title),
            UiText.Res(if (verified) R.string.studio_msg_verified else R.string.studio_msg_not_verified),
            if (downscaled) UiText.Res(R.string.studio_msg_downscaled) else null,
            extra,
        ),
        separator = " ",
    )

    private fun failMessage(error: Throwable): UiText {
        if (error is com.example.metacheck.domain.metadata.DailyLimitReachedException) AppPrompts.dailyLimitReached()
        if (error is com.example.metacheck.domain.model.ProRequiredException) AppPrompts.proRequired()
        return ErrorTexts.operationFailed(error)
    }

    companion object {
        /** Пустой файл, созданный диалогом, остаётся у пользователя: удалять его без операции не будем. */
        private val INTERRUPTED = UiText.Res(R.string.studio_msg_interrupted)

        val LOST_ACCESS = UiText.Res(R.string.error_access_lost)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MetaCheckApplication).container
                MetadataStudioViewModel(c.metadataStudio, createSavedStateHandle(), c.uriAccess, c.sharedCopies, c.folderAudit)
            }
        }
    }
}
