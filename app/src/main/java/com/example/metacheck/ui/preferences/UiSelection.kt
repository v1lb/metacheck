package com.example.metacheck.ui.preferences

import java.util.Locale

/** Presentation-only choices. Never stores images, API secrets or location. */
enum class AppLanguage { SYSTEM, RU, EN }
enum class AppAppearance { SYSTEM, DARK, LIGHT }

data class UiPreferencesState(
    val completed: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val appearance: AppAppearance = AppAppearance.SYSTEM,
)

object UiSelection {
    fun effectiveLanguage(choice: AppLanguage, systemLanguage: String): AppLanguage = when (choice) {
        AppLanguage.RU -> AppLanguage.RU
        AppLanguage.EN -> AppLanguage.EN
        AppLanguage.SYSTEM -> if (systemLanguage.lowercase(Locale.ROOT).startsWith("ru")) AppLanguage.RU else AppLanguage.EN
    }
    fun effectiveDark(choice: AppAppearance, systemDark: Boolean): Boolean = when (choice) {
        AppAppearance.SYSTEM -> systemDark
        AppAppearance.DARK -> true
        AppAppearance.LIGHT -> false
    }
}

