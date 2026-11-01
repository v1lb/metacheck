package com.example.metacheck.ui

import com.example.metacheck.ui.i18n.localize
import com.example.metacheck.ui.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutStringsTest {
    @Test fun aboutLabelsAreTranslated() {
        assertEquals("Open-source licenses", localize("Лицензии открытого кода", AppLanguage.EN))
        assertEquals("Privacy Policy", localize("Политика конфиденциальности", AppLanguage.EN))
        assertEquals("Terms of Use", localize("Условия использования", AppLanguage.EN))
    }

    @Test fun russianLabelsStayUnchanged() {
        assertEquals("Лицензии открытого кода", localize("Лицензии открытого кода", AppLanguage.RU))
    }

    @Test fun legalLinksUseHttpsOnTheLegalSite() {
        listOf(LegalLinks.PRIVACY_RU, LegalLinks.PRIVACY_EN, LegalLinks.TERMS_RU, LegalLinks.TERMS_EN, LegalLinks.SUPPORT)
            .forEach { assertTrue(it, it.startsWith("https://v1lb.github.io/legal-metacheck/")) }
    }

    @Test fun licenseNoticesMentionMapDataAndMainLibraries() {
        listOf("OpenStreetMap contributors", "osmdroid", "OkHttp", "Billing").forEach {
            assertTrue(it, LicenseNotices.TEXT.contains(it))
        }
    }
}
