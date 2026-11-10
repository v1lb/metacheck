package com.example.metacheck.data.parser

import com.example.metacheck.domain.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class FormatAndReaderTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun detectsJpeg() = assertEquals(ImageFormat.JPEG, ImageFormatDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))

    @Test
    fun detectsPng() = assertEquals(ImageFormat.PNG, ImageFormatDetector.detect(PNG_SIGNATURE + ByteArray(8)))

    @Test
    fun genericHeifBrandIsAvifOnlyWithAvifCompatibleBrand() {
        fun ftyp(vararg brands: String) = ("\u0000\u0000\u0000\u0020ftyp" + brands.first() + "\u0000\u0000\u0000\u0000" + brands.drop(1).joinToString("")).toByteArray(Charsets.ISO_8859_1)
        assertEquals(ImageFormat.HEIC, ImageFormatDetector.detect(ftyp("mif1", "mif1", "heic")))
        assertEquals(ImageFormat.AVIF, ImageFormatDetector.detect(ftyp("mif1", "mif1", "avif", "miaf")))
    }

    @Test
    fun detectsGif() = assertEquals(ImageFormat.GIF, ImageFormatDetector.detect("GIF89a....".toByteArray()))

    @Test
    fun detectsWebp() =
        assertEquals(ImageFormat.WEBP, ImageFormatDetector.detect("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))

    @Test
    fun detectsHeicAndAvif() {
        assertEquals(ImageFormat.HEIC, ImageFormatDetector.detect("\u0000\u0000\u0000\u0018ftypheic".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(ImageFormat.AVIF, ImageFormatDetector.detect("\u0000\u0000\u0000\u0018ftypavif".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun rejectsEverythingElse() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect("PK\u0003\u0004zip".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(ByteArray(0)))
        assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(bytes(0xFF)))
    }

    @Test
    fun boundedReaderTruncatesLargeStreams() {
        val (data, truncated) = readBounded(ByteArrayInputStream(ByteArray(1000)), 100)
        assertEquals(100, data.size)
        assertTrue(truncated)
    }

    @Test
    fun boundedReaderKeepsSmallStreams() {
        val (data, truncated) = readBounded(ByteArrayInputStream(ByteArray(50)), 100)
        assertEquals(50, data.size)
        assertFalse(truncated)
    }

    @Test
    fun boundedReaderExactLimitIsNotTruncated() {
        val (data, truncated) = readBounded(ByteArrayInputStream(ByteArray(100)), 100)
        assertEquals(100, data.size)
        assertFalse(truncated)
    }

    @Test
    fun indexOfSeqFindsAndBounds() {
        val hay = "abcXYZdef".toByteArray()
        assertEquals(3, hay.indexOfSeq("XYZ".toByteArray()))
        assertEquals(-1, hay.indexOfSeq("XYZ".toByteArray(), 0, 5))
        assertEquals(-1, hay.indexOfSeq(ByteArray(0)))
    }
}

/** Аудит, п. 12: известный размер -> буфер без удвоений и лишних копий; неверный размер не ломает чтение. */
class BoundedReaderSizeHintTest {
    private fun data(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun exactSizeHint() {
        val src = data(10_000)
        val (out, truncated) = readBounded(java.io.ByteArrayInputStream(src), 1_000_000, 10_000L)
        org.junit.Assert.assertArrayEquals(src, out)
        org.junit.Assert.assertFalse(truncated)
    }

    @Test fun hintTooSmall_stillReadsEverything() {
        val src = data(300_000)
        val (out, truncated) = readBounded(java.io.ByteArrayInputStream(src), 1_000_000, 10L)
        org.junit.Assert.assertArrayEquals(src, out)
        org.junit.Assert.assertFalse(truncated)
    }

    @Test fun hintTooLarge_trimsResult() {
        val src = data(500)
        val (out, _) = readBounded(java.io.ByteArrayInputStream(src), 1_000_000, 900_000L)
        org.junit.Assert.assertArrayEquals(src, out)
    }

    @Test fun limitRespectedWithHint() {
        val (out, truncated) = readBounded(java.io.ByteArrayInputStream(data(5_000)), 1_000, 5_000L)
        org.junit.Assert.assertEquals(1_000, out.size)
        org.junit.Assert.assertTrue(truncated)
    }

    @Test fun unknownSize_growsPastInitialBuffer() {
        val src = data(200_000)
        val (out, truncated) = readBounded(java.io.ByteArrayInputStream(src), 1_000_000)
        org.junit.Assert.assertArrayEquals(src, out)
        org.junit.Assert.assertFalse(truncated)
    }

    @Test fun zeroLimit() {
        val (out, truncated) = readBounded(java.io.ByteArrayInputStream(data(10)), 0, 10L)
        org.junit.Assert.assertEquals(0, out.size)
        org.junit.Assert.assertTrue(truncated)
    }
}
