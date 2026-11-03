package com.example.metacheck.ui

import com.example.metacheck.ui.preferences.AppAppearance
import com.example.metacheck.ui.preferences.AppLanguage
import com.example.metacheck.ui.preferences.StartDestination
import com.example.metacheck.ui.preferences.UiSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSelectionTest {
    // Язык хранит AppCompat; здесь проверяется разбор его тегов и перенос старого значения.
    @Test fun parsesAppCompatLanguageTags() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.RU, AppLanguage.fromTags("ru"))
        assertEquals(AppLanguage.RU, AppLanguage.fromTags("ru-RU,en-US"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTags("en-GB"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTags(" EN_us "))
        // Язык без перевода: интерфейс всё равно покажет values/, честнее считать это «как в системе».
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("de-DE"))
    }
    @Test fun everyExplicitLanguageHasATag() {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.forEach {
            assertEquals(it, AppLanguage.fromTags(it.tag!!))
        }
    }
    @Test fun legacyLanguageIsMigratedOnlyWhenExplicitAndNothingIsSetYet() {
        assertEquals(AppLanguage.RU, UiSelection.legacyLanguageToApply("RU", appLocalesEmpty = true))
        assertEquals(AppLanguage.EN, UiSelection.legacyLanguageToApply("EN", appLocalesEmpty = true))
        // Выбор в системных настройках важнее старого значения из DataStore.
        assertNull(UiSelection.legacyLanguageToApply("RU", appLocalesEmpty = false))
        assertNull(UiSelection.legacyLanguageToApply("SYSTEM", appLocalesEmpty = true))
        assertNull(UiSelection.legacyLanguageToApply(null, appLocalesEmpty = true))
        assertNull(UiSelection.legacyLanguageToApply("garbage", appLocalesEmpty = true))
    }
    @Test fun explicitDarkOverridesLightPhone() {
        assertTrue(UiSelection.effectiveDark(AppAppearance.DARK, false))
    }
    @Test fun systemAppearanceFollowsDevice() {
        assertTrue(UiSelection.effectiveDark(AppAppearance.SYSTEM, true))
        assertFalse(UiSelection.effectiveDark(AppAppearance.SYSTEM, false))
    }
    @Test fun lightOverridesDarkPhone() {
        assertFalse(UiSelection.effectiveDark(AppAppearance.LIGHT, true))
    }

    // Регрессия аудита, п. 6: пока настройки не прочитаны, онбординг показывать нельзя.
    @Test fun notLoaded_neverShowsOnboarding() {
        assertEquals(StartDestination.LOADING, UiSelection.startDestination(loaded = false, completed = false))
        assertEquals(StartDestination.LOADING, UiSelection.startDestination(loaded = false, completed = true))
    }
    @Test fun loaded_routesByCompletion() {
        assertEquals(StartDestination.ONBOARDING, UiSelection.startDestination(loaded = true, completed = false))
        assertEquals(StartDestination.MAIN, UiSelection.startDestination(loaded = true, completed = true))
    }
}
