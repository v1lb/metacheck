package com.example.metacheck.ui.preferences

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.metacheck.R
import java.util.Locale

/**
 * Язык приложения через AppCompat (per-app language).
 * Android 13+: значение хранит система (LocaleManager), оно видно в системных настройках приложения.
 * Android 8–12: значение хранит AppCompat (autoStoreLocales в манифесте).
 * После смены языка AppCompat пересоздаёт Activity; состояние экранов живёт в ViewModel и rememberSaveable.
 */
object AppLocales {
    fun current(): AppLanguage =
        AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    fun isUnset(): Boolean = AppCompatDelegate.getApplicationLocales().isEmpty

    fun apply(language: AppLanguage) {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        // Повторная установка того же значения лишний раз пересоздала бы Activity.
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    @StringRes
    fun nameRes(language: AppLanguage): Int = when (language) {
        AppLanguage.SYSTEM -> R.string.appearance_language_system
        AppLanguage.RU -> R.string.language_name_ru
        AppLanguage.EN -> R.string.language_name_en
    }

    /** Язык устройства, а не приложения: Resources.getSystem() не видит переопределение AppCompat. */
    fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    /** «Русский», «English», «Deutsch»: название языка на нём самом. */
    fun nativeName(locale: Locale): String =
        locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }.ifBlank { locale.toLanguageTag() }
}
