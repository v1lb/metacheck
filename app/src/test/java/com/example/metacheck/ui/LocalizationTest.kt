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
    @Test fun mapConsentTextsAreTranslatedAndRussianStaysUnchanged() {
        assertEquals("Show map (request to OpenStreetMap)", localize("Показать карту (запрос к OpenStreetMap)", AppLanguage.EN))
        assertEquals("Показать карту (запрос к OpenStreetMap)", localize("Показать карту (запрос к OpenStreetMap)", AppLanguage.RU))
    }
    @Test fun sizeAndBatchMessagesAreTranslated() {
        assertEquals("512 KB", localize("512 КБ", AppLanguage.EN))
        assertEquals("1.5 MB", localize("1.5 МБ", AppLanguage.EN))
        assertEquals("3/5 · Succeeded: 2 · Failed: 1", localize("3/5 · Успешно: 2 · Ошибок: 1", AppLanguage.EN))
    }
    @Test fun apiConfirmationIsTranslated() {
        assertEquals(
            "The JSON report will be sent to api.example.com. The original photo is not sent. GPS: excluded from the report.",
            localize("JSON-отчёт будет отправлен на api.example.com. Исходная фотография не отправляется. GPS: исключён из отчёта.", AppLanguage.EN),
        )
    }
    @Test fun composedStatusMessagesAreTranslated() {
        assertEquals("GPS removed from saved copy. Write verified", localize("GPS удалён из новой копии. Запись проверена", AppLanguage.EN))
        assertEquals("Pro active via key", localize("Pro активен по ключу", AppLanguage.EN))
    }
}
