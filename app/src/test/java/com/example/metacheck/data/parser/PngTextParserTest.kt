package com.example.metacheck.data.parser

import com.example.metacheck.data.parser.PngTestUtil.itxt
import com.example.metacheck.data.parser.PngTestUtil.png
import com.example.metacheck.data.parser.PngTestUtil.text
import com.example.metacheck.data.parser.PngTestUtil.ztxt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class PngTextParserTest {

    @Test
    fun readsPlainTextChunk() {
        val data = png(text("parameters", "a cat\nSteps: 20, Sampler: Euler"))
        val result = PngTextParser.parse(data)
        assertEquals(1, result.size)
        assertEquals("parameters", result[0].keyword)
        assertTrue(result[0].text.contains("Steps: 20"))
    }

    @Test
    fun readsCompressedChunk() {
        val data = png(ztxt("prompt", """{"3":{"class_type":"KSampler"}}""".toByteArray()))
        val result = PngTextParser.parse(data)
        assertEquals(1, result.size)
        assertTrue(result[0].text.contains("class_type"))
    }

    @Test
    fun readsInternationalTextAsUtf8() {
        val data = png(itxt("Description", "кошка на подоконнике"))
        val result = PngTextParser.parse(data)
        assertEquals("кошка на подоконнике", result.single().text)
    }

    @Test
    fun notPngReturnsEmpty() {
        assertTrue(PngTextParser.parse(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)).isEmpty())
    }

    @Test
    fun absurdChunkLengthDoesNotCrash() {
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        DataOutputStream(out).apply {
            writeInt(-1) // длина 4 ГБ как беззнаковое
            write("tEXt".toByteArray())
            write(ByteArray(16))
        }
        assertTrue(PngTextParser.parse(out.toByteArray()).isEmpty())
    }

    @Test
    fun truncatedFileDoesNotCrash() {
        val full = png(text("parameters", "x".repeat(500)), text("Software", "tool"))
        for (cut in listOf(9, 20, 100, full.size / 2, full.size - 1)) {
            PngTextParser.parse(full.copyOf(cut)) // главное: без исключений
        }
    }

    @Test
    fun decompressionBombIsRejected() {
        val bomb = ByteArray(16 * 1024 * 1024) // сожмётся до десятков КБ
        val data = png(ztxt("prompt", bomb))
        val result = PngTextParser.parse(data)
        assertEquals(PngTextParser.BAD_DATA, result.single().text)
    }

    @Test
    fun entryCountIsLimited() {
        val chunks = Array(PngTextParser.MAX_ENTRIES + 36) { text("k$it", "v") }
        val result = PngTextParser.parse(png(*chunks))
        assertEquals(PngTextParser.MAX_ENTRIES, result.size)
    }

    @Test
    fun controlCharactersAreStripped() {
        val data = png(text("Comment", "ok\u0001\u0007text"))
        assertEquals("oktext", PngTextParser.parse(data).single().text)
    }
}
