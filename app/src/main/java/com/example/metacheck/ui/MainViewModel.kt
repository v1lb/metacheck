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
import com.example.metacheck.R
import com.example.metacheck.domain.text.MetaTexts
import com.example.metacheck.ui.text.ErrorTexts
import com.example.metacheck.ui.text.UiText
import com.example.metacheck.domain.model.ApiException
import com.example.metacheck.domain.model.AppSettings
import com.example.metacheck.domain.model.HistoryEntry
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.ProRequiredException
import com.example.metacheck.domain.model.TooLargeException
import com.example.metacheck.domain.model.UnsupportedFormatException
import com.example.metacheck.domain.repository.EntitlementRepository
import com.example.metacheck.domain.repository.HistoryRepository
import com.example.metacheck.domain.repository.SettingsRepository
import com.example.metacheck.domain.usecase.AnalyzeImageUseCase
import com.example.metacheck.domain.usecase.CheckViaApiUseCase
import com.example.metacheck.domain.usecase.ExportReportUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Failed(val message: UiText) : UiState

    /** [source] есть только у свежего анализа; у отчёта из истории исходный файл недоступен. */
    data class Done(val report: MetaReport, val source: String?) : UiState
}

sealed interface ApiDialog {
    data object Loading : ApiDialog
    data class Success(val statusCode: Int, val body: String) : ApiDialog
    data class Error(val message: UiText) : ApiDialog
}

class MainViewModel(
    private val analyzeImage: AnalyzeImageUseCase,
    private val exportReport: ExportReportUseCase,
    private val checkViaApi: CheckViaApiUseCase,
    private val history: HistoryRepository,
    private val resolver: ContentResolver,
    settingsRepository: SettingsRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {

    private val settingsState = settingsRepository.settings
    val settings: StateFlow<AppSettings> = settingsState

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val historyEntries: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _apiDialog = MutableStateFlow<ApiDialog?>(null)
    val apiDialog: StateFlow<ApiDialog?> = _apiDialog.asStateFlow()

    /** Фото пришло через «Отправить без метаданных»: экран очищает его и открывает «Поделиться». */
    private val _cleanShareRequest = MutableStateFlow<String?>(null)
    val cleanShareRequest: StateFlow<String?> = _cleanShareRequest.asStateFlow()

    fun requestCleanShare(uri: Uri) {
        _cleanShareRequest.value = uri.toString()
        onImagePicked(uri) // отчёт тоже показываем: пользователь видит, что именно удалено
    }

    fun consumeCleanShare() {
        _cleanShareRequest.value = null
    }

    private var analyzeJob: Job? = null
    private var apiJob: Job? = null
    private var historyJob: Job? = null

    init {
        refreshHistory()
        // Сколько отчётов видно, зависит от Pro: после покупки (или её отмены) список перечитывается.
        viewModelScope.launch {
            entitlements.entitlement.map { it.isPro }.distinctUntilChanged().drop(1).collect { refreshHistory() }
        }
    }

    /** Новый запрос отменяет старый: иначе более ранний ответ мог бы перезаписать свежий список. */
    fun refreshHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            try { _history.value = history.list() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { showMessage(UiText.Res(R.string.main_msg_history_load_failed)) }
        }
    }

    fun showMessage(text: UiText) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** [saveToHistory] = false для повторного анализа очищенной копии: иначе на одно фото две записи. */
    fun onImagePicked(uri: Uri, saveToHistory: Boolean = true) {
        analyzeJob?.cancel()
        _state.value = UiState.Loading
        analyzeJob = viewModelScope.launch {
            try {
                analyzeImage(uri.toString()).fold(
                    onSuccess = { report ->
                        _state.value = UiState.Done(report, uri.toString())
                        if (saveToHistory && settingsState.value.saveHistory) {
                            try {
                                // save возвращает Result: без getOrThrow ошибка сохранения молча терялась.
                                history.save(report).getOrThrow()
                                refreshHistory()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { showMessage(UiText.Res(R.string.main_msg_history_save_failed)) }
                        }
                    },
                    onFailure = { _state.value = UiState.Failed(analysisError(it)) },
                )
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = UiState.Failed(UiText.Res(R.string.main_error_read_source)) }
        }
    }

    fun openFromHistory(id: String) {
        viewModelScope.launch {
            val report = try { history.load(id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            if (report == null) {
                showMessage(UiText.Res(R.string.main_msg_report_open_failed))
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
            catch (_: Exception) { showMessage(UiText.Res(R.string.main_msg_report_delete_failed)) }
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
                        ?: error("Cannot open output")
                }
                showMessage(UiText.Res(R.string.main_msg_history_csv_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showMessage(UiText.Res(R.string.main_msg_csv_failed))
            } finally {
                _busy.value = false
            }
        }
    }

    /** [texts] — тексты экрана, с которого запущен экспорт: файл будет на языке интерфейса. */
    fun exportMetadataCsv(target: Uri, texts: MetaTexts) = withDone { done ->
        withContext(Dispatchers.IO) {
            val data = MetadataCsv.encode(MetadataCatalog.from(done.report, texts))
            resolver.openOutputStream(target, "wt")?.use { it.write(data) }
                ?: error("Cannot open output")
        }
        showMessage(UiText.Res(R.string.main_msg_metadata_csv_saved))
    }

    fun exportJson(target: Uri, texts: MetaTexts) = withDone { done ->
        exportReport.json(done.report, target.toString(), texts).fold(
            onSuccess = { showMessage(UiText.Res(R.string.main_msg_json_saved)) },
            onFailure = { showMessage(exportError(it)) },
        )
    }

    fun exportPdf(target: Uri, texts: MetaTexts) = withDone { done ->
        exportReport.pdf(done.report, target.toString(), texts).fold(
            onSuccess = { showMessage(UiText.Res(R.string.main_msg_pdf_saved)) },
            onFailure = { showMessage(exportError(it)) },
        )
    }


    fun sendToApi(texts: MetaTexts) {
        val done = _state.value as? UiState.Done ?: return
        apiJob?.cancel()
        _apiDialog.value = ApiDialog.Loading
        apiJob = viewModelScope.launch {
            _apiDialog.value = checkViaApi(done.report, texts).fold(
                onSuccess = { ApiDialog.Success(it.statusCode, it.body) },
                onFailure = {
                    ApiDialog.Error(
                        when (it) {
                            is ApiException -> ErrorTexts.reason(it)
                            is ProRequiredException -> UiText.Res(R.string.main_api_pro_only)
                            else -> UiText.Res(R.string.api_error_request_failed)
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
                showMessage(UiText.Res(R.string.main_msg_operation_failed))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun exportError(e: Throwable): UiText =
        UiText.Res(if (e is ProRequiredException) R.string.main_msg_export_pro_only else R.string.main_msg_save_failed)

    private fun analysisError(e: Throwable): UiText = when (e) {
        is UnsupportedFormatException, is TooLargeException -> ErrorTexts.reason(e)
        is SecurityException -> UiText.Res(R.string.main_error_no_access)
        else -> UiText.Res(R.string.main_error_read_file)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MetaCheckApplication).container
                MainViewModel(c.analyzeImage, c.exportReport, c.checkViaApi, c.history, c.contentResolver, c.settings, c.entitlements)
            }
        }
    }
}
