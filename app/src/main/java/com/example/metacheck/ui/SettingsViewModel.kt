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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun update(transform: (AppSettings) -> AppSettings) = settingsRepository.update(transform)

    /** Возвращает false, если не удалось сохранить (например, Keystore недоступен). */
    fun saveKey(key: String): Boolean =
        try {
            settingsRepository.saveApiKey(key.trim().takeIf { it.isNotEmpty() })
            true
        } catch (e: Exception) {
            false
        }

    fun deleteKey() = settingsRepository.saveApiKey(null)

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
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
