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

/** Что показывать при запуске. LOADING: настройки ещё читаются, нельзя показывать ни онбординг, ни главный. */
enum class StartDestination { LOADING, ONBOARDING, MAIN }

object UiSelection {
    fun startDestination(loaded: Boolean, completed: Boolean): StartDestination = when {
        !loaded -> StartDestination.LOADING
        completed -> StartDestination.MAIN
        else -> StartDestination.ONBOARDING
    }

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

