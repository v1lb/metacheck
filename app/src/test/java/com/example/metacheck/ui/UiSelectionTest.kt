package com.example.metacheck.ui

import com.example.metacheck.ui.preferences.AppAppearance
import com.example.metacheck.ui.preferences.AppLanguage
import com.example.metacheck.ui.preferences.UiSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSelectionTest {
    @Test fun systemRussianSelectsRussian() {
        assertEquals(AppLanguage.RU, UiSelection.effectiveLanguage(AppLanguage.SYSTEM, "ru"))
    }
    @Test fun systemEnglishSelectsEnglish() {
        assertEquals(AppLanguage.EN, UiSelection.effectiveLanguage(AppLanguage.SYSTEM, "en"))
    }
    @Test fun explicitEnglishOverridesRussianPhone() {
        assertEquals(AppLanguage.EN, UiSelection.effectiveLanguage(AppLanguage.EN, "ru"))
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
}
