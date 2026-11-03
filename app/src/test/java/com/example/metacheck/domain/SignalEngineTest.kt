package com.example.metacheck.domain

import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.analysis.SignalInput
import com.example.metacheck.domain.model.C2paFacts
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
        val s = eval(SignalInput(hasGps = true, c2pa = C2paFacts(parsed = true), truncated = true,
            toolFields = listOf(MetaItem("EXIF Software", "ComfyUI")),
            freeTextFields = listOf(MetaItem("EXIF UserComment", "made with dall-e"))))
        assertTrue(s.toString(), s.all { it.kind != null && it.title.isEmpty() && it.detail.isEmpty() })
    }

    @Test
    fun evidenceComesFirst() {
        val s = eval(SignalInput(hasGps = true, c2pa = C2paFacts(parsed = true), toolFields = listOf(MetaItem("EXIF Software", "ComfyUI"))))
        assertEquals(Severity.AI_EVIDENCE, s.first().severity)
        assertEquals(Severity.INFO, s.last().severity)
    }

    // --- C2PA ---

    @Test
    fun c2paAiLabelIsEvidenceWithTheModelName() {
        val s = eval(SignalInput(c2pa = C2paFacts(true, "ChatGPT", setOf(C2paFacts.TRAINED), listOf("GPT-4o"))))
        val signal = s.evidence().single()
        assertEquals(SignalKind.C2PA_AI_CREATED, signal.kind)
        assertEquals(listOf("GPT-4o"), signal.args)
    }

    @Test
    fun c2paCompositeIsEditedNotCreated() {
        val s = eval(SignalInput(c2pa = C2paFacts(true, "Adobe Photoshop", setOf(C2paFacts.COMPOSITE_TRAINED))))
        assertEquals(SignalKind.C2PA_AI_EDITED, s.evidence().single().kind)
        assertEquals(listOf("Adobe Photoshop"), s.evidence().single().args)
    }

    @Test
    fun c2paFromCameraIsOnlyInformation() {
        val s = eval(SignalInput(c2pa = C2paFacts(true, "Leica M11-P", setOf("digitalCapture"))))
        assertTrue(s.evidence().isEmpty())
        assertEquals(listOf("Leica M11-P"), s.single { it.kind == SignalKind.C2PA_PRESENT }.args)
    }

    @Test
    fun c2paWrittenByGeneratorWithoutLabelStillCounts() {
        val s = eval(SignalInput(c2pa = C2paFacts(true, "ChatGPT", emptySet())))
        assertEquals("chatgpt", s.evidence().single { it.kind == SignalKind.AI_TOOL_FIELD }.args[0])
    }

    @Test
    fun unreadableC2paIsReportedHonestly() {
        val s = eval(SignalInput(c2pa = C2paFacts(parsed = false)))
        assertEquals(listOf("", SignalEngine.C2PA_UNREADABLE), s.single { it.kind == SignalKind.C2PA_PRESENT }.args)
    }

    // --- IPTC ---

    @Test
    fun iptcCompositeIsEditedWithAi() {
        val s = eval(SignalInput(xmpFields = mapOf(SignalEngine.KEY_DIGITAL_SOURCE_TYPE to
            "http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia")))
        assertEquals(SignalKind.IPTC_AI_EDITED, s.evidence().single().kind)
    }

    @Test
    fun iptcDigitalCaptureIsNotEvidence() {
        val s = eval(SignalInput(xmpFields = mapOf(SignalEngine.KEY_DIGITAL_SOURCE_TYPE to
            "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture")))
        assertTrue(s.evidence().isEmpty())
    }

    // --- Параметры генерации ---

    @Test
    fun a1111ParametersInJpegUserComment() {
        val s = eval(SignalInput(freeTextFields = listOf(MetaItem("EXIF UserComment",
            "a cat\nNegative prompt: blurry\nSteps: 30, Sampler: DPM++ 2M, CFG scale: 7, Seed: 42"))))
        val signal = s.evidence().single()
        assertEquals(SignalKind.GENERATION_PARAMS, signal.kind)
        assertEquals(listOf("EXIF UserComment"), signal.args)
    }

    @Test
    fun novelAiCommentJson() {
        val s = eval(SignalInput(pngText = listOf(
            PngTextEntry("Comment", "{\"prompt\": \"girl\", \"steps\": 28, \"sampler\": \"k_euler\", \"seed\": 1}"))))
        assertEquals(SignalKind.GENERATION_PARAMS, s.evidence().single().kind)
    }

    @Test
    fun ordinaryJsonCommentIsNotEvidence() {
        val s = eval(SignalInput(pngText = listOf(PngTextEntry("Comment", "{\"album\": \"trip\", \"steps\": 3}"))))
        assertTrue(s.evidence().isEmpty())
    }

    // --- Названия генераторов ---

    @Test
    fun newGeneratorsAreRecognizedInSoftwareField() {
        for (software in listOf("Google Gemini", "Grok (xAI Aurora)", "Ideogram", "FLUX.1 [dev]", "Microsoft Designer",
            "Recraft", "Kandinsky 3.1", "Shedevrum", "Draw Things", "Fooocus 2.5")) {
            val s = eval(SignalInput(toolFields = listOf(MetaItem("EXIF Software", software))))
            assertEquals(software, 1, s.evidence().size)
        }
    }

    @Test
    fun ordinarySoftwareIsNotMistakenForAGenerator() {
        for (software in listOf("Aurora HDR 2019", "Editor de imagen 2.1", "Adobe Photoshop 25.0", "HDR+ 1.0.540104767zd",
            "GIMP 2.10", "Snapseed", "Emulator Camera", "Gemini Photos", "Lightroom Classic", "Fluxbox")) {
            val s = eval(SignalInput(toolFields = listOf(MetaItem("EXIF Software", software))))
            assertTrue(software, s.evidence().isEmpty())
        }
    }

    @Test
    fun ambiguousWordsInCaptionsAreIgnored() {
        for (caption in listOf("Gemini season", "Aurora borealis over Tromso", "My cat Sora")) {
            val s = eval(SignalInput(freeTextFields = listOf(MetaItem("EXIF ImageDescription", caption))))
            assertTrue(caption, s.none { it.kind == SignalKind.AI_MENTION_TEXT })
        }
    }

    // --- Почему метаданных нет ---

    @Test
    fun messengerCopiesAndScreenshotsAreExplained() {
        assertEquals("WhatsApp", SignalEngine.copySource("IMG-20261101-WA0007.jpg"))
        assertEquals("Telegram", SignalEngine.copySource("photo_2026-11-01_12-30-45.jpg"))
        assertEquals("Facebook", SignalEngine.copySource("FB_IMG_1730000000000.jpg"))
        assertEquals("Facebook / Instagram", SignalEngine.copySource("465123456_1234567890123456_789_n.jpg"))
        assertEquals("screenshot", SignalEngine.copySource("Screenshot_20261101-123000.png"))
        assertEquals(null, SignalEngine.copySource("IMG_20261101_123000.jpg"))
        assertEquals(null, SignalEngine.copySource("DSC01234.JPG"))

        val s = eval(SignalInput(fileName = "IMG-20261101-WA0007.jpg"))
        assertEquals(listOf("WhatsApp"), s.single { it.kind == SignalKind.MESSENGER_COPY }.args)
        assertTrue(eval(SignalInput(fileName = "Screenshot_1.png")).any { it.kind == SignalKind.SCREENSHOT })
    }

    @Test
    fun copyHintIsNotShownWhenEvidenceExists() {
        val s = eval(SignalInput(fileName = "IMG-20261101-WA0007.jpg",
            toolFields = listOf(MetaItem("EXIF Software", "Midjourney"))))
        assertTrue(s.none { it.kind == SignalKind.MESSENGER_COPY })
    }
}
