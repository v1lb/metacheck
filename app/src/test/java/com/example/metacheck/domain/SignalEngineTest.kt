package com.example.metacheck.domain

import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.analysis.SignalInput
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.PngTextEntry
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.SignalKind
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
        assertEquals(SignalKind.SD_PARAMETERS, s.evidence().single().kind)
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
        val iptc = s.evidence().single()
        assertEquals(SignalKind.IPTC_AI_SOURCE, iptc.kind)
        assertEquals(listOf("http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"), iptc.args)
    }

    @Test
    fun toolFieldMarkerIsEvidence() {
        val s = eval(SignalInput(toolFields = listOf(MetaItem("EXIF Software", "Midjourney v6"))))
        assertEquals(1, s.evidence().size)
    }

    @Test
    fun freeTextMarkerIsOnlyCaution() {
        val s = eval(SignalInput(freeTextFields = listOf(MetaItem("EXIF ImageDescription", "Firefly over a meadow"))))
        assertTrue(s.evidence().isEmpty())
        assertTrue(s.any { it.severity == Severity.CAUTION })
    }

    @Test
    fun gpsRaisesPrivacyCaution() {
        val s = eval(SignalInput(hasGps = true))
        assertTrue(s.any { it.kind == SignalKind.HAS_GPS })
    }

    @Test
    fun noMetadataIsCautionNotVerdict() {
        val s = SignalEngine.evaluate(SignalInput(hasAnyMetadata = false))
        assertTrue(s.any { it.kind == SignalKind.NO_METADATA })
        assertTrue(s.evidence().isEmpty())
    }

    @Test
    fun cleanFileGetsInfoOnly() {
        val s = eval(SignalInput())
        assertEquals(1, s.size)
        assertEquals(Severity.INFO, s.single().severity)
    }

    @Test
    fun toolFieldSignalCarriesMarkerFieldAndValueForTheUi() {
        val s = eval(SignalInput(toolFields = listOf(MetaItem("EXIF Software", "Midjourney v6"))))
        val signal = s.evidence().single()
        assertEquals(SignalKind.AI_TOOL_FIELD, signal.kind)
        assertEquals(3, signal.args.size)
        assertEquals("EXIF Software", signal.args[1])
        assertEquals("Midjourney v6", signal.args[2])
    }

    @Test
    fun everySignalHasAKindSoTheUiCanTranslateIt() {
        val s = eval(SignalInput(hasGps = true, hasC2pa = true, truncated = true,
            toolFields = listOf(MetaItem("EXIF Software", "ComfyUI")),
            freeTextFields = listOf(MetaItem("EXIF UserComment", "made with dall-e"))))
        assertTrue(s.toString(), s.all { it.kind != null && it.title.isEmpty() && it.detail.isEmpty() })
    }

    @Test
    fun evidenceComesFirst() {
        val s = eval(SignalInput(hasGps = true, hasC2pa = true, toolFields = listOf(MetaItem("EXIF Software", "ComfyUI"))))
        assertEquals(Severity.AI_EVIDENCE, s.first().severity)
        assertEquals(Severity.INFO, s.last().severity)
    }
}
