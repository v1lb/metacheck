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
import com.example.metacheck.data.UriAccess
import com.example.metacheck.domain.metadata.BatchProgress
import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataStudioUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Operations survive ordinary Compose recomposition; work is on Dispatchers.IO in repository. */
class MetadataStudioViewModel(
    private val studio: MetadataStudioUseCase,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val uriAccess: UriAccess? = null,
) : ViewModel() {
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
    private val _message = MutableStateFlow<String?>(null)
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
    fun stripCopy(
        source: String,
        destination: Uri,
        onSavedCopy: (() -> Unit)? = null
    ) = work {
        studio.strip(source, destination.toString()).fold(
            onSuccess = {
                _message.value = successMessage("Очищенная копия сохранена", it.verified, it.downscaled)
                if (it.verified) onSavedCopy?.invoke()
            },
            onFailure = { _message.value = failMessage(it) },
        )
        usedToday.value = studio.usedToday()
    }

    fun wipeGpsCopy(source: String, destination: Uri) = work {
        studio.wipeGps(source, destination.toString()).fold(
            onSuccess = {
                _message.value = successMessage("GPS удалён из новой копии", it.verified, it.downscaled) +
                    if (it.verified) ". Для отправки используйте новую копию, а не оригинал" else ""
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
            onSuccess = { _message.value = successMessage("EXIF записан в новый файл", it.verified, false) },
            onFailure = { _message.value = failMessage(it) },
        )
    }

    private fun overwrite(source: String, backup: Uri, patch: MetadataPatch?) = work {
        studio.overwrite(source, backup.toString(), patch).fold(
            onSuccess = { _message.value = successMessage("Оригинал заменён. Резервная копия сохранена", it.verified, it.downscaled) },
            onFailure = { _message.value = failMessage(it) },
        )
        usedToday.value = studio.usedToday()
    }

    fun batchStrip(sources: List<String>, targetTree: Uri) = work {
        _batch.value = BatchProgress(total = sources.size, running = true)
        studio.batch(sources, targetTree.toString()) { _batch.value = it }.fold(
            onSuccess = {
                _message.value = "Пакетная очистка завершена: сохранено ${it.succeeded}, ошибок ${it.failed}."
                _batch.value = it
            },
            onFailure = {
                _batch.value = _batch.value.copy(running = false)
                _message.value = failMessage(it)
            },
        )
    }

    private fun work(operation: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = failMessage(e) }
            finally { _busy.value = false }
        }
    }

    private fun successMessage(title: String, verified: Boolean, downscaled: Boolean): String =
        title + (if (!verified) ". Не удалось перепроверить запись: откройте файл вручную" else ". Запись проверена") +
            if (downscaled) ". Разрешение уменьшено для безопасной обработки" else ""

    private fun failMessage(error: Throwable): String =
        if (error.isAccessLost()) LOST_ACCESS
        else "Операция не выполнена: " + (error.message ?: "ошибка доступа или формата").take(250)

    private fun Throwable.isAccessLost(): Boolean =
        generateSequence(this) { it.cause }.take(8).any { it is SecurityException }

    companion object {
        /** Пустой файл, созданный диалогом, остаётся у пользователя: удалять его без операции не будем. */
        private const val INTERRUPTED =
            "Операция прервана: приложение было перезапущено системой. Повторите действие; созданный пустой файл можно удалить"

        const val LOST_ACCESS =
            "Нет доступа к файлу: Android отозвал разрешение после перезапуска приложения. Выберите файл снова"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MetaCheckApplication).container
                MetadataStudioViewModel(c.metadataStudio, createSavedStateHandle(), c.uriAccess)
            }
        }
    }
}
