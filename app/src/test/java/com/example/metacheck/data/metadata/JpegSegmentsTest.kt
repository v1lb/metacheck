package com.example.metacheck.data.metadata

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class JpegSegmentsTest {
    private fun segment(marker: Int, data: ByteArray): ByteArray {
        val len = data.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (len shr 8).toByte(), len.toByte()) + data
    }
    @Test fun stripsXmpAndIptcButKeepsExifAndEntropy() {
        val source = File.createTempFile("jpeg_test_", ".jpg")
        val destination = File.createTempFile("jpeg_clean_", ".jpg")
        try {
            val jpeg = ByteArrayOutputStream().apply {
                write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
                write(segment(0xE1, "Exif\u0000\u0000camera".toByteArray()))
                write(segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000gps".toByteArray()))
                write(segment(0xED, "Photoshop 3.0\u0000Iptc".toByteArray()))
                write(segment(0xFE, "old comment".toByteArray()))
                write(segment(0xDA, byteArrayOf(1, 2)))
                write(byteArrayOf(7, 8, 9, 0xFF.toByte(), 0xD9.toByte()))
            }.toByteArray()
            source.writeBytes(jpeg)
            JpegSegments.removeContradictorySegments(source, destination)
            val result = destination.readBytes().toString(Charsets.ISO_8859_1)
            assertTrue(result.contains("Exif"))
            assertFalse(result.contains("Photoshop"))
            assertFalse(result.contains("xap/1.0"))
            assertFalse(result.contains("old comment"))
            assertTrue(result.contains("\u0007\u0008\u0009"))
            assertFalse(JpegSegments.isStrippedJpeg(destination))
        } finally { source.delete(); destination.delete() }
    }
    @Test fun recognisesFreshJpegWithoutMetadata() {
        val file = File.createTempFile("jpeg_stripped_", ".jpg")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
                segment(0xDA, byteArrayOf(1, 2)) + byteArrayOf(3, 4, 0xFF.toByte(), 0xD9.toByte()))
            assertTrue(JpegSegments.isStrippedJpeg(file))
        } finally { file.delete() }
    }
}
