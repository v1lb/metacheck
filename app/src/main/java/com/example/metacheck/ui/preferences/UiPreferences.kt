package com.example.metacheck.ui.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences synchronously commits three tiny settings before onboarding is dismissed. */
class UiPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui_preferences_v3", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state: StateFlow<UiPreferencesState> = mutable.asStateFlow()

    private fun read() = UiPreferencesState(
        completed = prefs.getBoolean("completed", false),
        language = runCatching { AppLanguage.valueOf(prefs.getString("language", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(AppLanguage.SYSTEM),
        appearance = runCatching { AppAppearance.valueOf(prefs.getString("appearance", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(AppAppearance.SYSTEM),
    )

    @Synchronized
    fun update(language: AppLanguage = state.value.language,
               appearance: AppAppearance = state.value.appearance,
               completed: Boolean = state.value.completed): Boolean {
        val ok = prefs.edit().putString("language", language.name)
            .putString("appearance", appearance.name).putBoolean("completed", completed).commit()
        if (ok) mutable.value = UiPreferencesState(completed, language, appearance)
        return ok
    }
}
