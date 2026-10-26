package com.example.metacheck.domain

import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.analysis.SignalInput
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.PngTextEntry
import com.example.metacheck.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {

    private fun eval(input: SignalInput) = SignalEngine.evaluate(input.copy(hasAnyMetadata = true))

    private fun List<com.example.metacheck.domain.model.Signal>.evidence() =
        filter { it.severity == Severity.AI_EVIDENCE }

    @Test
    fun detectsStableDiffusionParameters() {
        val s = eval(SignalInput(pngText = listOf(PngTextEntry("parameters", "cat\nSteps: 20, Sampler: Euler a, Seed: 1"))))
        assertEquals("Параметры Stable Diffusion", s.evidence().single().title)
    }

    @Test
    fun parametersWithoutGenerationFieldsIsNotEvidence() {
        val s = eval(SignalInput(pngText = listOf(PngTextEntry("parameters", "just some note"))))
        assertTrue(s.evidence().isEmpty())
    }

    @Test
    fun detectsComfyUiOnceEvenWithTwoBlocks() {
        val s = eval(
            SignalInput(
                pngText = listOf(
                    PngTextEntry("prompt", "{\"3\":{\"class_type\":\"KSampler\"}}"),
                    PngTextEntry("workflow", "{\"nodes\":[]}"),
                ),
            ),
        )
        assertEquals(1, s.evidence().size)
    }

    @Test
    fun detectsIptcDigitalSourceType() {
        val s = eval(
            SignalInput(
                xmpFields = mapOf(SignalEngine.KEY_DIGITAL_SOURCE_TYPE to "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"),
            ),
        )
        assertEquals("Метка IPTC: создано ИИ", s.evidence().single().title)
    }

    @Test
    fun toolFieldMarkerIsEvidence() {
        val s = eval(SignalInput(toolFields = listOf(MetaItem("EXIF Software", "Midjourney v6"))))
        assertEquals(1, s.evidence().size)
    }

    @Test
    fun freeTextMarkerIsOnlyCaution() {
        val s = eval(SignalInput(freeTextFields = listOf(MetaItem("EXIF Описание", "Firefly over a meadow"))))
        assertTrue(s.evidence().isEmpty())
        assertTrue(s.any { it.severity == Severity.CAUTION })
    }

    @Test
    fun gpsRaisesPrivacyCaution() {
        val s = eval(SignalInput(hasGps = true))
        assertTrue(s.any { it.title == "В файле есть геолокация" })
    }

    @Test
    fun noMetadataIsCautionNotVerdict() {
        val s = SignalEngine.evaluate(SignalInput(hasAnyMetadata = false))
        assertTrue(s.any { it.title == "Метаданных нет" })
        assertTrue(s.evidence().isEmpty())
    }

    @Test
    fun cleanFileGetsInfoOnly() {
        val s = eval(SignalInput())
        assertEquals(1, s.size)
        assertEquals(Severity.INFO, s.single().severity)
    }

    @Test
    fun evidenceComesFirst() {
        val s = eval(SignalInput(hasGps = true, hasC2pa = true, toolFields = listOf(MetaItem("EXIF Software", "ComfyUI"))))
        assertEquals(Severity.AI_EVIDENCE, s.first().severity)
        assertEquals(Severity.INFO, s.last().severity)
    }
}
