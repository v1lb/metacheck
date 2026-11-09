package com.example.metacheck.data.metadata

import com.example.metacheck.data.parser.IptcReader
import com.example.metacheck.data.parser.XmpExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class DescriptiveMetadataTest {
    private fun segment(marker: Int, data: ByteArray): ByteArray {
        val len = data.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (len shr 8).toByte(), len.toByte()) + data
    }

    private val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF><rdf:Description
        exif:GPSLatitude="48,51.5N" xmp:CreateDate="2020-01-01" photoshop:City="Paris">
        <dc:subject><rdf:Bag><rdf:li>tower</rdf:li><rdf:li>night &amp; lights</rdf:li></rdf:Bag></dc:subject>
        <dc:title><rdf:Alt><rdf:li xml:lang="x-default">Eiffel</rdf:li></rdf:Alt></dc:title>
        </rdf:Description></rdf:RDF></x:xmpmeta>"""

    private fun sourceJpeg() = ByteArrayOutputStream().apply {
        write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        write(segment(0xE0, "JFIF\u0000\u0001\u0001".toByteArray()))
        write(segment(0xE1, "Exif\u0000\u0000camera".toByteArray()))
        write(segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000".toByteArray() + xmp.toByteArray()))
        write(segment(0xDA, byteArrayOf(1, 2)))
        write(byteArrayOf(7, 8, 9, 0xFF.toByte(), 0xD9.toByte()))
    }.toByteArray()

    @Test fun keywordsSurviveRewriteWithoutCoordinatesOrDates() {
        val source = File.createTempFile("desc_src_", ".jpg")
        val cleaned = File.createTempFile("desc_clean_", ".jpg")
        val result = File.createTempFile("desc_out_", ".jpg")
        try {
            source.writeBytes(sourceJpeg())
            val fields = DescriptiveMetadata.read(source)
            assertEquals(listOf("tower", "night & lights"), fields.keywords)
            assertEquals("Eiffel", fields.title)
            assertEquals("Paris", fields.city)

            JpegSegments.removeContradictorySegments(source, cleaned)
            JpegSegments.insertAfterHeader(cleaned, result, fields.segments())
            val bytes = result.readBytes()

            val packet = XmpExtractor.extractPacket(bytes)!!
            val parsed = XmpExtractor.parse(packet).fields
            assertEquals("tower, night & lights", parsed["dc:subject"])
            assertEquals("Paris", parsed["photoshop:City"])
            assertFalse(packet.contains("GPSLatitude"))
            assertFalse(packet.contains("CreateDate"))
            assertEquals(listOf("tower", "night & lights"), IptcReader.fields(bytes)["Keywords"])

            // EXIF остаётся первым после JFIF, сжатые данные не тронуты, структура читается.
            val text = bytes.toString(Charsets.ISO_8859_1)
            assertTrue(text.indexOf("Exif") < text.indexOf("xap/1.0"))
            assertTrue(text.contains("\u0007\u0008\u0009"))
            assertTrue(JpegSegments.inspect(result).let { it.exif && it.xmp && it.iptc && !it.trailer })
        } finally { source.delete(); cleaned.delete(); result.delete() }
    }

    @Test fun placesAreDroppedWhenLocationChanges() {
        val d = DescriptiveMetadata.from(mapOf("photoshop:City" to "Rome", "dc:subject" to "a, b"), mapOf("Country" to listOf("Italy")))
        val noPlaces = d.withoutPlaces()
        assertNull(noPlaces.city); assertNull(noPlaces.country)
        assertEquals(listOf("a", "b"), noPlaces.keywords)
    }

    @Test fun nothingToKeepWritesNothing() {
        assertTrue(DescriptiveMetadata.from(emptyMap(), emptyMap()).segments().isEmpty())
    }
}
