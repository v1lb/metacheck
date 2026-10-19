package com.example.metacheck.ui.preferences

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val Context.uiDataStore by preferencesDataStore(
    name = "ui_preferences_v4",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "ui_preferences_v3")) },
)

/** Persistent app language and appearance, with automatic migration of v2.3 preferences. */
class UiPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val legacy = appContext.getSharedPreferences("ui_preferences_v3", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(readLegacy())
    val state: StateFlow<UiPreferencesState> = mutable.asStateFlow()

    init {
        scope.launch {
            appContext.uiDataStore.data.catch { emit(emptyPreferences()) }.collect { preferences ->
                mutable.value = preferences.asUiState()
            }
        }
    }

    private fun readLegacy() = UiPreferencesState(
        completed = legacy.getBoolean("completed", false),
        language = runCatching { AppLanguage.valueOf(legacy.getString("language", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(AppLanguage.SYSTEM),
        appearance = runCatching { AppAppearance.valueOf(legacy.getString("appearance", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(AppAppearance.SYSTEM),
    )

    private fun Preferences.asUiState() = UiPreferencesState(
        completed = this[KEY_COMPLETE] ?: false,
        language = runCatching { AppLanguage.valueOf(this[KEY_LANGUAGE] ?: "SYSTEM") }
            .getOrDefault(AppLanguage.SYSTEM),
        appearance = runCatching { AppAppearance.valueOf(this[KEY_APPEARANCE] ?: "SYSTEM") }
            .getOrDefault(AppAppearance.SYSTEM),
    )

    /** The choice is emitted only after DataStore actually commits it. */
    suspend fun update(
        language: AppLanguage? = null,
        appearance: AppAppearance? = null,
        completed: Boolean? = null,
    ): Boolean = try {
        val stored = appContext.uiDataStore.edit { preferences ->
            if (language != null) preferences[KEY_LANGUAGE] = language.name
            if (appearance != null) preferences[KEY_APPEARANCE] = appearance.name
            if (completed != null) preferences[KEY_COMPLETE] = completed
        }
        mutable.value = stored.asUiState()
        true
    } catch (_: Exception) { false }

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_APPEARANCE = stringPreferencesKey("appearance")
        val KEY_COMPLETE = booleanPreferencesKey("completed")
    }
}
