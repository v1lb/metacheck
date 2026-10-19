package com.example.metacheck.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.metacheck.MetaCheckApplication
import com.example.metacheck.domain.model.ApiException
import com.example.metacheck.domain.model.AppSettings
import com.example.metacheck.domain.model.HistoryEntry
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.model.TooLargeException
import com.example.metacheck.domain.model.UnsupportedFormatException
import com.example.metacheck.domain.repository.HistoryRepository
import com.example.metacheck.domain.repository.SettingsRepository
import com.example.metacheck.domain.usecase.AnalyzeImageUseCase
import com.example.metacheck.domain.usecase.CheckViaApiUseCase
import com.example.metacheck.domain.usecase.CleanMetadataUseCase
import com.example.metacheck.domain.usecase.ExportReportUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Failed(val message: String) : UiState

    /** [source] есть только у свежего анализа; у отчёта из истории исходный файл недоступен. */
    data class Done(val report: MetaReport, val source: String?) : UiState
}

sealed interface ApiDialog {
    data object Loading : ApiDialog
    data class Success(val statusCode: Int, val body: String) : ApiDialog
    data class Error(val message: String) : ApiDialog
}

class MainViewModel(
    private val analyzeImage: AnalyzeImageUseCase,
    private val exportReport: ExportReportUseCase,
    private val cleanMetadata: CleanMetadataUseCase,
    private val checkViaApi: CheckViaApiUseCase,
    private val history: HistoryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val settingsState = settingsRepository.settings
    val settings: StateFlow<AppSettings> = settingsState

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val historyEntries: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _apiDialog = MutableStateFlow<ApiDialog?>(null)
    val apiDialog: StateFlow<ApiDialog?> = _apiDialog.asStateFlow()

    private var analyzeJob: Job? = null
    private var apiJob: Job? = null

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch { _history.value = history.list() }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun onImagePicked(uri: Uri) {
        analyzeJob?.cancel()
        _state.value = UiState.Loading
        analyzeJob = viewModelScope.launch {
            analyzeImage(uri.toString()).fold(
                onSuccess = { report ->
                    _state.value = UiState.Done(report, uri.toString())
                    if (settingsState.value.saveHistory) {
                        history.save(report)
                        refreshHistory()
                    }
                },
                onFailure = { _state.value = UiState.Failed(it.toUserMessage()) },
            )
        }
    }

    fun openFromHistory(id: String) {
        viewModelScope.launch {
            val report = history.load(id)
            if (report == null) {
                showMessage("Не удалось открыть отчёт")
                refreshHistory()
            } else {
                _state.value = UiState.Done(report, null)
            }
        }
    }

    fun deleteFromHistory(id: String) {
        viewModelScope.launch {
            history.delete(id)
            refreshHistory()
        }
    }

    fun close() {
        analyzeJob?.cancel()
        _state.value = UiState.Idle
        refreshHistory()
    }

    fun exportJson(target: Uri) = withDone { done ->
        exportReport.json(done.report, target.toString()).fold(
            onSuccess = { showMessage("JSON-отчёт сохранён") },
            onFailure = { showMessage(exportError(it)) },
        )
    }

    fun exportPdf(target: Uri) = withDone { done ->
        exportReport.pdf(done.report, target.toString()).fold(
            onSuccess = { showMessage("PDF-отчёт сохранён") },
            onFailure = { showMessage(exportError(it)) },
        )
    }

    fun saveCleaned(target: Uri) = withDone { done ->
        val source = done.source ?: return@withDone
        cleanMetadata(source, target.toString(), done.report.file.format).fold(
            onSuccess = {
                showMessage(
                    if (it.downscaled) "Метаданные удалены. Копия уменьшена, чтобы поместиться в память."
                    else "Метаданные удалены. Копия сохранена, исходный файл не изменён.",
                )
            },
            onFailure = {
                showMessage(
                    when (it) {
                        is TooLargeException -> "Изображение слишком большое для очистки."
                        is UnsupportedFormatException -> "Этот формат нельзя пересохранить."
                        else -> "Не удалось очистить метаданные."
                    },
                )
            },
        )
    }

    fun sendToApi() {
        val done = _state.value as? UiState.Done ?: return
        apiJob?.cancel()
        _apiDialog.value = ApiDialog.Loading
        apiJob = viewModelScope.launch {
            _apiDialog.value = checkViaApi(done.report).fold(
                onSuccess = { ApiDialog.Success(it.statusCode, it.body) },
                onFailure = {
                    ApiDialog.Error(
                        when (it) {
                            is ApiException -> it.message ?: "Не удалось выполнить запрос."
                            is ProRequiredException -> "Отправка в API доступна в Pro."
                            else -> "Не удалось выполнить запрос."
                        },
                    )
                },
            )
        }
    }

    fun dismissApiDialog() {
        apiJob?.cancel()
        _apiDialog.value = null
    }

    private fun withDone(block: suspend (UiState.Done) -> Unit) {
        val done = _state.value as? UiState.Done ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                block(done)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun exportError(e: Throwable): String =
        if (e is ProRequiredException) "Экспорт доступен в Pro" else "Не удалось сохранить файл"

    private fun Throwable.toUserMessage(): String = when (this) {
        is UnsupportedFormatException -> "Это не изображение в поддерживаемом формате (JPEG, PNG, WebP, GIF, HEIC, AVIF)."
        else -> "Не удалось прочитать файл. Попробуй другой."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MetaCheckApplication).container
                MainViewModel(c.analyzeImage, c.exportReport, c.cleanMetadata, c.checkViaApi, c.history, c.settings)
            }
        }
    }
}
