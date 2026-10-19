package com.example.metacheck.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.metacheck.MetaCheckApplication
import com.example.metacheck.domain.metadata.BatchProgress
import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataStudioUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Operations survive ordinary Compose recomposition; work is on Dispatchers.IO in repository. */
class MetadataStudioViewModel(private val studio: MetadataStudioUseCase) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _batch = MutableStateFlow(BatchProgress())
    val batch = _batch.asStateFlow()
    val usedToday = MutableStateFlow(studio.usedToday())

    fun clearMessage() { _message.value = null }
    fun stripCopy(source: String, destination: Uri) = work {
        studio.strip(source, destination.toString()).fold(
            onSuccess = { _message.value = successMessage("Очищенная копия сохранена", it.verified, it.downscaled) },
            onFailure = { _message.value = failMessage(it) },
        )
        usedToday.value = studio.usedToday()
    }

    fun wipeGpsCopy(source: String, destination: Uri) = work {
        studio.wipeGps(source, destination.toString()).fold(
            onSuccess = { _message.value = successMessage("GPS удалён из новой копии", it.verified, it.downscaled) },
            onFailure = { _message.value = failMessage(it) },
        )
        usedToday.value = studio.usedToday()
    }

    fun editCopy(source: String, destination: Uri, patch: MetadataPatch) = work {
        studio.edit(source, destination.toString(), patch).fold(
            onSuccess = { _message.value = successMessage("EXIF записан в новый файл", it.verified, false) },
            onFailure = { _message.value = failMessage(it) },
        )
    }

    fun overwrite(source: String, backup: Uri, patch: MetadataPatch?) = work {
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
        "Операция не выполнена: " + (error.message ?: "ошибка доступа или формата").take(250)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MetaCheckApplication).container
                MetadataStudioViewModel(c.metadataStudio)
            }
        }
    }
}
