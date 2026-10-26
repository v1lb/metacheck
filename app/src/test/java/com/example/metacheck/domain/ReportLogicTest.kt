package com.example.metacheck.domain

import com.example.metacheck.domain.api.ApiConfigValidator
import com.example.metacheck.domain.export.CleanFormat
import com.example.metacheck.domain.export.ReportText
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.sampleReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLogicTest {

    // --- ReportText ---

    @Test
    fun textContainsKeyFacts() {
        val text = ReportText.format(sampleReport(name = "cat.png", ai = true))
        assertTrue(text.contains("cat.png"))
        assertTrue(text.contains("Следы ИИ"))
        assertTrue(text.contains("Модель: Pixel 8"))
        assertTrue(text.contains("55.750000, 37.620000"))
    }

    @Test
    fun textOmitsGpsWhenAbsent() {
        assertTrue(!ReportText.format(sampleReport(gps = false)).contains("Геолокация"))
    }

    @Test
    fun longValuesAreTruncated() {
        val text = ReportText.format(sampleReport(prompt = "a".repeat(5000)))
        val line = text.lines().first { it.startsWith("parameters: ") }
        assertEquals("parameters: ".length + 2000, line.length)
    }

    // --- CleanFormat ---

    @Test
    fun jpegStaysJpegOthersBecomePng() {
        assertEquals("jpg", CleanFormat.outputFor(ImageFormat.JPEG).extension)
        for (f in listOf(ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.HEIC, ImageFormat.GIF, ImageFormat.AVIF)) {
            assertEquals("image/png", CleanFormat.outputFor(f).mimeType)
        }
    }

    // --- ApiConfigValidator ---

    @Test
    fun acceptsPlainHttpsUrl() {
        assertNull(ApiConfigValidator.validateUrl("https://api.example.org/v1/check"))
        assertNull(ApiConfigValidator.validateUrl("  https://api.example.org:8443/x  "))
    }

    @Test
    fun rejectsDangerousOrBrokenUrls() {
        for (bad in listOf(
            "",
            "http://api.example.org",
            "ftp://api.example.org",
            "api.example.org/check",
            "https://user:pass@api.example.org",
            "https://api.example.org/a b",
            "https:///nohost",
            "https://" + "a".repeat(3000),
        )) {
            assertNotNull("должен быть отклонён: $bad", ApiConfigValidator.validateUrl(bad))
        }
    }

    @Test
    fun headerNames() {
        assertNull(ApiConfigValidator.validateHeaderName("X-Api-Key"))
        assertNotNull(ApiConfigValidator.validateHeaderName("Bad Header"))
        assertNotNull(ApiConfigValidator.validateHeaderName("X\r\nInjected"))
        assertNotNull(ApiConfigValidator.validateHeaderName(""))
    }

    @Test
    fun pins() {
        assertNull(ApiConfigValidator.validatePin(""))
        assertNull(ApiConfigValidator.validatePin("A".repeat(43) + "="))
        assertNotNull(ApiConfigValidator.validatePin("short"))
        assertNotNull(ApiConfigValidator.validatePin("A".repeat(44)))
    }
}
