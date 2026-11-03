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

/**
 * Persistent appearance and onboarding state, with automatic migration of v2.3 preferences.
 *
 * Пока DataStore не прочитан, [loaded] = false и экран не должен решать «онбординг или главный».
 * Раньше начальное значение бралось из старых SharedPreferences, которые SharedPreferencesMigration
 * уже удалила. В итоге completed=false, и онбординг мелькал на каждом холодном старте.
 */
class UiPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(UiPreferencesState())
    val state: StateFlow<UiPreferencesState> = mutable.asStateFlow()

    private val mutableLoaded = MutableStateFlow(false)
    /** true после первого чтения DataStore (или ошибки чтения: тогда действуют значения по умолчанию). */
    val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()

    init {
        scope.launch {
            appContext.uiDataStore.data.catch { emit(emptyPreferences()) }.collect { preferences ->
                mutable.value = preferences.asUiState()
                mutableLoaded.value = true
            }
        }
    }

    private fun Preferences.asUiState() = UiPreferencesState(
        completed = this[KEY_COMPLETE] ?: false,
        appearance = runCatching { AppAppearance.valueOf(this[KEY_APPEARANCE] ?: "SYSTEM") }
            .getOrDefault(AppAppearance.SYSTEM),
        legacyLanguage = this[KEY_LANGUAGE],
    )

    /**
     * The choice is emitted only after DataStore actually commits it.
     * Язык здесь не хранится: его хранит AppCompat/система, см. AppLocales.
     */
    suspend fun update(
        appearance: AppAppearance? = null,
        completed: Boolean? = null,
    ): Boolean = try {
        val stored = appContext.uiDataStore.edit { preferences ->
            if (appearance != null) preferences[KEY_APPEARANCE] = appearance.name
            if (completed != null) preferences[KEY_COMPLETE] = completed
        }
        mutable.value = stored.asUiState()
        mutableLoaded.value = true
        true
    } catch (_: Exception) { false }

    /**
     * Удалить язык, сохранённый прежними версиями. Запускается в собственном scope:
     * смена языка пересоздаёт Activity, и её корутины отменились бы на полпути.
     */
    fun clearLegacyLanguage() {
        scope.launch {
            try {
                val stored = appContext.uiDataStore.edit { it.remove(KEY_LANGUAGE) }
                mutable.value = stored.asUiState()
            } catch (_: Exception) {
                // Не удалось: перенос повторится при следующем запуске и ничего не сломает.
            }
        }
    }

    private companion object {
        /** Только для переноса со старых версий. */
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_APPEARANCE = stringPreferencesKey("appearance")
        val KEY_COMPLETE = booleanPreferencesKey("completed")
    }
}
