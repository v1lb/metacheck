package com.example.metacheck.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class IptcReaderTest {

    private fun dataset(record: Int, number: Int, value: ByteArray) = ByteArrayOutputStream().apply {
        write(0x1C); write(record); write(number); write(value.size shr 8); write(value.size and 0xFF); write(value)
    }.toByteArray()

    private fun jpegWithIim(iim: ByteArray): ByteArray {
        val resource = ByteArrayOutputStream().apply {
            write("8BIM".toByteArray()); write(0x04); write(0x04)
            write(0); write(0) // пустое имя, дополненное до чётной длины
            write(iim.size ushr 24); write(iim.size ushr 16 and 0xFF); write(iim.size ushr 8 and 0xFF); write(iim.size and 0xFF)
            write(iim); if (iim.size % 2 == 1) write(0)
        }.toByteArray()
        val payload = "Photoshop 3.0\u0000".toByteArray(Charsets.US_ASCII) + resource
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xED.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte()) +
            payload + byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2, 0xFF.toByte(), 0xD9.toByte())
    }

    @Test
    fun readsKeywordsAndPlaceInUtf8() {
        val iim = dataset(1, 90, byteArrayOf(0x1B, 0x25, 0x47)) +
            dataset(2, 25, "море".toByteArray()) + dataset(2, 25, "отпуск".toByteArray()) +
            dataset(2, 90, "Сочи".toByteArray()) + dataset(2, 101, "Россия".toByteArray()) +
            dataset(2, 80, "Ivan Petrov".toByteArray())
        val items = IptcReader.read(jpegWithIim(iim)).associate { it.key to it.value }
        assertEquals("море, отпуск", items["iptc:Keywords"])
        assertEquals("Сочи", items["iptc:City"])
        assertEquals("Россия", items["iptc:Country"])
        assertEquals("Ivan Petrov", items["iptc:Byline"])
    }

    @Test
    fun latin1WithoutCharsetMarker() {
        val iim = dataset(2, 90, byteArrayOf('M'.code.toByte(), 0xFC.toByte(), 'n'.code.toByte()))
        assertEquals("Mün", IptcReader.read(jpegWithIim(iim)).single().value)
    }

    @Test
    fun brokenOrMissingDataGivesNothing() {
        assertTrue(IptcReader.read(byteArrayOf(1, 2, 3)).isEmpty())
        val truncated = jpegWithIim(dataset(2, 25, "a".toByteArray())).copyOf(20)
        assertTrue(IptcReader.read(truncated).isEmpty())
    }

    @Test
    fun xmpKeywordsBagIsJoined() {
        val packet = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF><rdf:Description photoshop:City="Paris">
            <dc:subject><rdf:Bag><rdf:li>tower</rdf:li><rdf:li>night</rdf:li></rdf:Bag></dc:subject>
            </rdf:Description></rdf:RDF></x:xmpmeta>"""
        val fields = XmpExtractor.parse(packet).fields
        assertEquals("tower, night", fields["dc:subject"])
        assertEquals("Paris", fields["photoshop:City"])
    }
}
