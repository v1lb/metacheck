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
