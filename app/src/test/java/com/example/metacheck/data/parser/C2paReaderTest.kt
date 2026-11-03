package com.example.metacheck.data.parser

import com.example.metacheck.domain.model.C2paFacts
import com.example.metacheck.domain.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Random

/**
 * Синтетические файлы с хранилищем C2PA, собранные по спецификации: JUMBF-блоки, CBOR-утверждения,
 * встраивание в JPEG (несколько APP11), PNG (caBX), WebP (C2PA) и HEIC (uuid).
 */
class C2paReaderTest {

    // --- CBOR-кодировщик для тестов ---

    private fun ByteArrayOutputStream.head(major: Int, value: Long) {
        val m = major shl 5
        when {
            value < 24 -> write(m or value.toInt())
            value < 0x100 -> { write(m or 24); write(value.toInt()) }
            value < 0x10000 -> { write(m or 25); write((value shr 8).toInt()); write(value.toInt() and 0xFF) }
            else -> { write(m or 26); for (s in intArrayOf(24, 16, 8, 0)) write(((value shr s) and 0xFF).toInt()) }
        }
    }

    private fun cbor(v: Any?): ByteArray = ByteArrayOutputStream().also { enc(it, v) }.toByteArray()

    private fun enc(o: ByteArrayOutputStream, v: Any?) {
        when (v) {
            null -> o.write(0xF6)
            is Boolean -> o.write(if (v) 0xF5 else 0xF4)
            is Int -> if (v >= 0) o.head(0, v.toLong()) else o.head(1, (-1 - v).toLong())
            is String -> v.toByteArray().let { o.head(3, it.size.toLong()); o.write(it) }
            is ByteArray -> { o.head(2, v.size.toLong()); o.write(v) }
            is List<*> -> { o.head(4, v.size.toLong()); v.forEach { enc(o, it) } }
            is Map<*, *> -> { o.head(5, v.size.toLong()); v.forEach { (k, x) -> enc(o, k); enc(o, x) } }
            else -> error("unsupported $v")
        }
    }

    // --- JUMBF ---

    private fun box(type: String, payload: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        val size = payload.size + 8
        for (s in intArrayOf(24, 16, 8, 0)) write((size shr s) and 0xFF)
        write(type.toByteArray(Charsets.ISO_8859_1)); write(payload)
    }.toByteArray()

    private fun superbox(label: String, vararg content: ByteArray): ByteArray {
        val desc = ByteArray(16) + byteArrayOf(0x03) + label.toByteArray() + byteArrayOf(0)
        return box("jumb", box("jumd", desc) + content.fold(ByteArray(0)) { a, b -> a + b })
    }

    private fun manifest(generator: Any, actions: List<Map<String, Any>>, label: String = "urn:uuid:1") = superbox(
        label,
        superbox("c2pa.assertions", superbox("c2pa.actions.v2", box("cbor", cbor(mapOf("actions" to actions))))),
        superbox("c2pa.claim.v2", box("cbor", cbor(mapOf("claim_generator_info" to generator, "alg" to "sha256")))),
        superbox("c2pa.signature", box("cbor", cbor(listOf(ByteArray(4))))),
    )

    private val aiAction = mapOf(
        "action" to "c2pa.created",
        "digitalSourceType" to "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia",
        "softwareAgent" to mapOf("name" to "GPT-4o"),
    )

    private val chatGptStore = superbox("c2pa", manifest(mapOf("name" to "ChatGPT"), listOf(aiAction)))

    // --- Контейнеры ---

    private fun jpeg(store: ByteArray, segmentPayload: Int = 60000): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        // APP1 с мусором, чтобы обход сегментов был настоящим.
        out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 6, 1, 2, 3, 4))
        val header = store.copyOfRange(0, 8)
        var offset = 0
        var seq = 1
        while (offset < store.size) {
            val body = if (seq == 1) store.copyOfRange(0, minOf(store.size, segmentPayload))
            else header + store.copyOfRange(offset, minOf(store.size, offset + segmentPayload))
            offset += if (seq == 1) body.size else body.size - 8
            val seg = byteArrayOf('J'.code.toByte(), 'P'.code.toByte(), 0, 1, 0, 0, 0, seq.toByte()) + body
            val len = seg.size + 2
            out.write(byteArrayOf(0xFF.toByte(), 0xEB.toByte(), (len shr 8).toByte(), len.toByte()))
            out.write(seg)
            seq++
        }
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2, 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun pngChunk(type: String, data: ByteArray) = ByteArrayOutputStream().apply {
        for (s in intArrayOf(24, 16, 8, 0)) write((data.size shr s) and 0xFF)
        write(type.toByteArray()); write(data); write(ByteArray(4))
    }.toByteArray()

    private fun png(store: ByteArray) = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) +
        pngChunk("IHDR", ByteArray(13)) + pngChunk("caBX", store) + pngChunk("IDAT", ByteArray(10)) + pngChunk("IEND", ByteArray(0))

    private fun webp(store: ByteArray): ByteArray {
        fun chunk(type: String, d: ByteArray) = type.toByteArray() +
            byteArrayOf(d.size.toByte(), (d.size shr 8).toByte(), (d.size shr 16).toByte(), (d.size shr 24).toByte()) +
            d + (if (d.size % 2 == 1) byteArrayOf(0) else ByteArray(0))
        val body = "WEBP".toByteArray() + chunk("VP8X", ByteArray(10)) + chunk("C2PA", store) + chunk("VP8 ", ByteArray(7))
        return "RIFF".toByteArray() + byteArrayOf(body.size.toByte(), (body.size shr 8).toByte(), (body.size shr 16).toByte(), 0) + body
    }

    private fun heic(store: ByteArray): ByteArray {
        val uuid = byteArrayOf(
            0xD8.toByte(), 0xFE.toByte(), 0xC3.toByte(), 0xD6.toByte(), 0x1B, 0x0E, 0x48, 0x3C,
            0x92.toByte(), 0x97.toByte(), 0x58, 0x28, 0x87.toByte(), 0x7E, 0xC4.toByte(), 0x81.toByte(),
        )
        val payload = uuid + ByteArray(4) + "manifest".toByteArray() + byteArrayOf(0) + ByteArray(8) + store
        return box("ftyp", "heic".toByteArray() + ByteArray(4)) + box("uuid", payload) + box("mdat", ByteArray(20))
    }

    // --- Тесты ---

    @Test fun readsChatGptLabelFromJpegSplitAcrossSegments() {
        val facts = C2paReader.read(jpeg(chatGptStore, segmentPayload = 40), ImageFormat.JPEG)!!
        assertTrue(facts.parsed)
        assertTrue(facts.aiCreated)
        assertEquals("ChatGPT", facts.generator)
        assertEquals(listOf("GPT-4o"), facts.aiAgents)
    }

    @Test fun readsPngWebpAndHeic() {
        for ((format, file) in listOf(ImageFormat.PNG to png(chatGptStore), ImageFormat.WEBP to webp(chatGptStore),
            ImageFormat.HEIC to heic(chatGptStore))) {
            val facts = C2paReader.read(file, format)
            assertTrue("$format", facts?.aiCreated == true)
        }
    }

    @Test fun fallsBackToScanningWhenContainerIsUnusual() {
        val file = "GIF89a".toByteArray() + ByteArray(30) + chatGptStore + ByteArray(5)
        assertTrue(C2paReader.read(file, ImageFormat.GIF)!!.aiCreated)
    }

    @Test fun compositeEditAndLegacyGeneratorFields() {
        val edit = mapOf(
            "action" to "c2pa.edited",
            "digitalSourceType" to "http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia",
            "softwareAgent" to "Adobe Firefly",
        )
        val camera = mapOf("action" to "c2pa.created", "digitalSourceType" to "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture")
        // v1: claim_generator_info — массив; ингредиент (снимок камеры) идёт первым, активный манифест последним.
        val store = superbox("c2pa",
            manifest(listOf(mapOf("name" to "Leica", "version" to "1.0")), listOf(camera), "urn:uuid:a"),
            manifest(listOf(mapOf("name" to "Adobe Photoshop", "version" to "26.0")), listOf(edit), "urn:uuid:b"))
        val facts = C2paReader.read(png(store), ImageFormat.PNG)!!
        assertTrue(facts.aiEdited)
        assertFalse(facts.aiCreated)
        assertEquals("Adobe Photoshop 26.0", facts.generator)
        assertEquals(listOf("Adobe Firefly"), facts.aiAgents)
        assertEquals(setOf("digitalCapture", C2paFacts.COMPOSITE_TRAINED), facts.sourceTypes)
    }

    @Test fun fileWithoutC2paGivesNull() {
        assertNull(C2paReader.read(png(ByteArray(0)), ImageFormat.PNG))
        assertNull(C2paReader.read(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()), ImageFormat.JPEG))
    }

    @Test fun truncatedStoreDoesNotCrashAndIsNotCalledAi() {
        val cut = chatGptStore.copyOfRange(0, chatGptStore.size - 60)
        val facts = C2paReader.read(png(cut), ImageFormat.PNG)
        assertTrue(facts != null)
        assertFalse(facts!!.parsed && facts.generator == null && facts.aiCreated)
    }

    @Test fun randomBytesNeverThrow() {
        val random = Random(42)
        repeat(300) {
            val data = ByteArray(random.nextInt(2000)).also(random::nextBytes)
            // Вставляем «jumb»/«c2pa», чтобы путь разбора действительно запускался.
            if (data.size > 40) {
                "jumb".toByteArray().copyInto(data, 20); "c2pa".toByteArray().copyInto(data, 30)
            }
            for (f in ImageFormat.entries) C2paReader.read(data, f)
        }
    }

    @Test fun cborRejectsHugeLengthsWithoutAllocating() {
        // Массив «на 2^32 элементов» в 5 байтах.
        val evil = byteArrayOf(0x9A.toByte(), 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertTrue(runCatching { Cbor.decode(evil) }.exceptionOrNull() is Cbor.CborException)
        val deep = ByteArray(1000) { 0x81.toByte() }
        assertTrue(runCatching { Cbor.decode(deep) }.exceptionOrNull() is Cbor.CborException)
    }

    @Test fun cborDecodesCommonTypes() {
        val v = Cbor.decode(cbor(mapOf("a" to listOf(1, -2, "x", true, null), "b" to ByteArray(3)))) as Map<*, *>
        assertEquals(listOf(1L, -2L, "x", true, null), v["a"])
        assertEquals(3, (v["b"] as ByteArray).size)
    }
}
