package com.example.metacheck.data.metadata

import com.example.metacheck.data.parser.PngTestUtil.chunk
import com.example.metacheck.data.parser.PngTestUtil.png
import com.example.metacheck.data.parser.PngTestUtil.text
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PngCleanCheckTest {

    private fun clean(bytes: ByteArray) = PngCleanCheck.isClean(ByteArrayInputStream(bytes))
    private val ihdr = chunk("IHDR", ByteArray(13))
    private val idat = chunk("IDAT", ByteArray(12))

    @Test fun plainImageIsClean() {
        assertTrue(clean(png(ihdr, idat)))
        assertTrue(clean(png(ihdr, chunk("sRGB", byteArrayOf(0)), chunk("gAMA", ByteArray(4)), idat)))
    }

    /** Аудит, п. 21: Android сам пишет iCCP для Display P3, такая очищенная копия должна проходить. */
    @Test fun colorProfileIsAllowedOnce() {
        val icc = chunk("iCCP", "Display P3".toByteArray() + byteArrayOf(0, 0) + ByteArray(500))
        assertTrue(clean(png(ihdr, icc, idat)))
        assertFalse(clean(png(ihdr, icc, icc, idat)))
    }

    @Test fun metadataAndUnknownChunksAreRejected() {
        for (bad in listOf(
            text("Comment", "hi"), chunk("eXIf", ByteArray(20)), chunk("zTXt", ByteArray(8)), chunk("iTXt", ByteArray(8)),
            chunk("tIME", ByteArray(7)), chunk("caBX", ByteArray(30)), chunk("prVt", ByteArray(4)),
        )) assertFalse(String(bad, 4, 4, Charsets.ISO_8859_1), clean(png(ihdr, bad, idat)))
    }

    @Test fun trailerAfterIendIsRejected() {
        assertFalse(clean(png(ihdr, idat) + "hidden".toByteArray()))
    }

    @Test fun brokenFilesAreRejected() {
        val good = png(ihdr, idat)
        assertFalse(clean(good.copyOfRange(0, good.size - 6)))
        assertFalse(clean(ByteArray(8)))
        assertFalse(clean(png(idat))) // первым должен идти IHDR
        // Длина блока больше файла и огромная длина.
        val huge = good.copyOf().also { it[8] = 0x7F; it[9] = 0xFF.toByte() }
        assertFalse(clean(huge))
    }
}
