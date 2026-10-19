package com.example.metacheck.ui

import android.net.Uri
import android.content.ContentResolver
import com.example.metacheck.domain.export.HistoryCsv
import com.example.metacheck.domain.export.MetadataCsv
import com.example.metacheck.domain.metadata.MetadataCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
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
    private val checkViaApi: CheckViaApiUseCase,
    private val history: HistoryRepository,
    private val resolver: ContentResolver,
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
        viewModelScope.launch {
            try { _history.value = history.list() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { showMessage("Не удалось загрузить историю") }
        }
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
            try {
                analyzeImage(uri.toString()).fold(
                    onSuccess = { report ->
                        _state.value = UiState.Done(report, uri.toString())
                        if (settingsState.value.saveHistory) {
                            try {
                                history.save(report)
                                refreshHistory()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { showMessage("Проверка завершена, но историю сохранить не удалось") }
                        }
                    },
                    onFailure = { _state.value = UiState.Failed(it.toUserMessage()) },
                )
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = UiState.Failed("Ошибка чтения файла. Попробуйте другой источник.") }
        }
    }

    fun openFromHistory(id: String) {
        viewModelScope.launch {
            val report = try { history.load(id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
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
            try { history.delete(id); refreshHistory() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { showMessage("Не удалось удалить отчёт") }
        }
    }

    fun close() {
        analyzeJob?.cancel()
        _state.value = UiState.Idle
        refreshHistory()
    }

    fun exportHistoryCsv(target: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    val data = HistoryCsv.encode(history.list())
                    resolver.openOutputStream(target, "wt")?.use { it.write(data) }
                        ?: error("Невозможно открыть файл")
                }
                showMessage("CSV с историей сохранён")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showMessage("Не удалось сохранить CSV")
            } finally {
                _busy.value = false
            }
        }
    }

    fun exportMetadataCsv(target: Uri) = withDone { done ->
        withContext(Dispatchers.IO) {
            val data = MetadataCsv.encode(MetadataCatalog.from(done.report))
            resolver.openOutputStream(target, "wt")?.use { it.write(data) }
                ?: error("Не удалось открыть файл")
        }
        showMessage("CSV с метаданными сохранён")
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showMessage("Не удалось завершить операцию")
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
                MainViewModel(c.analyzeImage, c.exportReport, c.checkViaApi, c.history, c.contentResolver, c.settings)
            }
        }
    }
}
