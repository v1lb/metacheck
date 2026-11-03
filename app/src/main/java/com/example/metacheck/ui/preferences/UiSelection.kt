package com.example.metacheck.ui.preferences

import java.util.Locale

/**
 * Языки интерфейса. Сам выбор хранит не приложение, а AppCompat/система (см. [AppLocales]):
 * так на Android 13+ язык меняется и в системных настройках «Приложения → MetaCheck → Язык».
 *
 * Добавить язык: строки в tools/i18n (появится res/values-xx), запись здесь и language_name_xx.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null), RU("ru"), EN("en");

    companion object {
        /** По списку тегов вида "ru-RU,en". Пусто или незнакомый язык: SYSTEM. */
        fun fromTags(tags: String): AppLanguage {
            val first = tags.substringBefore(',').trim()
            if (first.isEmpty()) return SYSTEM
            val language = first.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }

        /** Значение из DataStore прежних версий ("RU", "EN", "SYSTEM"); всё прочее считаем SYSTEM. */
        fun fromLegacy(value: String?): AppLanguage = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class AppAppearance { SYSTEM, DARK, LIGHT }

/**
 * Presentation-only choices. Never stores images, API secrets or location.
 * [legacyLanguage]: язык, сохранённый прежними версиями. Переносится в AppCompat один раз и удаляется.
 */
data class UiPreferencesState(
    val completed: Boolean = false,
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val legacyLanguage: String? = null,
)

/** Что показывать при запуске. LOADING: настройки ещё читаются, нельзя показывать ни онбординг, ни главный. */
enum class StartDestination { LOADING, ONBOARDING, MAIN }

object UiSelection {
    fun startDestination(loaded: Boolean, completed: Boolean): StartDestination = when {
        !loaded -> StartDestination.LOADING
        completed -> StartDestination.MAIN
        else -> StartDestination.ONBOARDING
    }

    fun effectiveDark(choice: AppAppearance, systemDark: Boolean): Boolean = when (choice) {
        AppAppearance.SYSTEM -> systemDark
        AppAppearance.DARK -> true
        AppAppearance.LIGHT -> false
    }

    /**
     * Нужно ли переносить старый язык. Только если пользователь его явно выбирал (RU/EN)
     * и в AppCompat ещё ничего не задано: выбор в системных настройках важнее старого значения.
     */
    fun legacyLanguageToApply(legacy: String?, appLocalesEmpty: Boolean): AppLanguage? {
        val language = AppLanguage.fromLegacy(legacy)
        return if (language != AppLanguage.SYSTEM && appLocalesEmpty) language else null
    }
}
