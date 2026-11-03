package com.example.metacheck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.metacheck.MetaCheckApplication
import com.example.metacheck.domain.model.AppSettings
import com.example.metacheck.domain.repository.HistoryRepository
import com.example.metacheck.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun update(transform: (AppSettings) -> AppSettings) = settingsRepository.update(transform)

    /**
     * Шифрование идёт через Keystore (межпроцессный вызов, до сотен мс на слабых телефонах),
     * поэтому только в фоне. [onResult] вызывается в главном потоке: false, если сохранить не удалось.
     */
    fun saveKey(key: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    settingsRepository.saveApiKey(key.trim().takeIf { it.isNotEmpty() })
                    true
                } catch (e: Exception) {
                    false
                }
            }
            onResult(ok)
        }
    }

    fun deleteKey() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { settingsRepository.saveApiKey(null) } }
    }

    /** [onDone] вызывается в главном потоке после фактического удаления. */
    /** onDone(true) только если история действительно удалена: иначе пользователь думал бы, что данных нет. */
    fun clearHistory(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = try { history.clear(); true } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            onDone(ok)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MetaCheckApplication).container
                SettingsViewModel(c.settings, c.history)
            }
        }
    }
}
