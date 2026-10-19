package com.example.metacheck.data.parser

import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.model.PngTextEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class XmpExtractorTest {

    private val packet = """
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description xmp:CreatorTool="Midjourney &amp; Co"
                Iptc4xmpExt:DigitalSourceType="http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia">
              <dc:title><rdf:Alt><rdf:li xml:lang="x-default">Закат</rdf:li></rdf:Alt></dc:title>
              <dc:creator><rdf:Seq><rdf:li>Анна</rdf:li><rdf:li>Борис</rdf:li></rdf:Seq></dc:creator>
            </rdf:Description>
          </rdf:RDF>
        </x:xmpmeta>
    """.trimIndent()

    @Test
    fun parsesAttributesAndElements() {
        val data = XmpExtractor.parse(packet)
        assertEquals("Midjourney & Co", data.fields[XmpExtractor.KEY_CREATOR_TOOL])
        assertEquals("Закат", data.fields["dc:title"])
        assertEquals("Анна, Борис", data.fields["dc:creator"])
        assertNotNull(data.fields[SignalEngine.KEY_DIGITAL_SOURCE_TYPE])
    }

    @Test
    fun recognizesExplicitXmpGpsFields() {
        val xmp = """<x:xmpmeta><rdf:Description exif:GPSLatitude="41,54.168N" exif:GPSLongitude="12,29.784E" /></x:xmpmeta>"""
        val parsed = XmpExtractor.parse(xmp)
        assertEquals("41,54.168N", parsed.fields["exif:GPSLatitude"])
        assertEquals("12,29.784E", parsed.fields["exif:GPSLongitude"])
    }

    @Test
    fun extractsPacketFromBinaryNoise() {
        val noise = ByteArray(100) { 0x7F }
        val bytes = noise + packet.toByteArray() + noise
        val found = XmpExtractor.extractPacket(bytes)
        assertNotNull(found)
        assertEquals(packet, found)
    }

    @Test
    fun returnsNullWithoutPacket() {
        assertNull(XmpExtractor.extractPacket(ByteArray(1000)))
    }

    @Test
    fun unterminatedPacketIsIgnored() {
        val bytes = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"> no end".toByteArray()
        assertNull(XmpExtractor.extractPacket(bytes))
    }

    @Test
    fun prefersPngXmpEntry() {
        val entry = PngTextEntry("XML:com.adobe.xmp", "<x:xmpmeta>from png</x:xmpmeta>")
        assertEquals(entry.text, XmpExtractor.extractPacket(ByteArray(10), listOf(entry)))
    }

    @Test
    fun externalEntityIsNotResolved() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <x:xmpmeta><rdf:Description xmp:CreatorTool="&xxe;"/></x:xmpmeta>""".trimIndent()
        val data = XmpExtractor.parse(evil)
        assertEquals("&xxe;", data.fields[XmpExtractor.KEY_CREATOR_TOOL])
    }

    @Test
    fun c2paNeedsBothMarkers() {
        assertEquals(false, C2paDetector.looksPresent("jumb only".toByteArray()))
        assertEquals(true, C2paDetector.looksPresent("xx jumb yy c2pa zz".toByteArray()))
    }
}
