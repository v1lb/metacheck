package com.example.metacheck.ui

import com.example.metacheck.ui.i18n.localize
import com.example.metacheck.ui.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {
    @Test fun englishDashboardTranslation() {
        assertEquals("SCAN HISTORY", localize("ИСТОРИЯ ПРОВЕРОК", AppLanguage.EN))
    }
    @Test fun russianIsUnmodified() {
        assertEquals("Исследовать изображение", localize("Исследовать изображение", AppLanguage.RU))
    }
    @Test fun dynamicFieldCount() {
        assertEquals("4 fields · on-device viewer", localize("4 полей · просмотр прямо на устройстве", AppLanguage.EN))
    }
    @Test fun forensicValuesAreNeverModified() {
        assertEquals("CameraModel-X", localize("CameraModel-X", AppLanguage.EN))
    }
}
