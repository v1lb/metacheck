package com.example.metacheck.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Регрессия аудита, п. 11: подготовленный XMP не должен подвешивать анализ (ReDoS). */
class XmpRedosTest {

    private fun timeMs(block: () -> Unit): Long {
        val start = System.nanoTime(); block(); return (System.nanoTime() - start) / 1_000_000
    }

    @Test fun manyUnclosedElements_parseQuickly() {
        val evil = buildString {
            append("<x:xmpmeta>")
            while (length < XmpExtractor.MAX_PACKET) append("<dc:title>a")
            append("</x:xmpmeta>")
        }
        val ms = timeMs { XmpExtractor.parse(evil) }
        // Старая версия: O(n²), десятки секунд и больше. Новая: линейная, обычно < 200 мс.
        assertTrue("parse took $ms ms", ms < 3_000)
    }

    @Test fun manyAttributeNamesWithoutValues_parseQuickly() {
        val evil = buildString {
            append("<x:xmpmeta>")
            while (length < XmpExtractor.MAX_PACKET) append(" xmp:CreatorTool = \"")
        }
        val ms = timeMs { XmpExtractor.parse(evil) }
        assertTrue("parse took $ms ms", ms < 3_000)
    }

    @Test fun unclosedInnerTags_parseQuickly() {
        val evil = "<x:xmpmeta><dc:description>" + "<".repeat(XmpExtractor.MAX_PACKET) + "</dc:description></x:xmpmeta>"
        val ms = timeMs { XmpExtractor.parse(evil) }
        assertTrue("parse took $ms ms", ms < 3_000)
    }

    @Test fun matchesPerFieldAreCapped() {
        val packet = "<x:xmpmeta>" + (1..500).joinToString("") { "<stEvt:softwareAgent>Tool $it</stEvt:softwareAgent>" } + "</x:xmpmeta>"
        val values = XmpExtractor.valuesOf(packet, XmpExtractor.KEY_SOFTWARE_AGENT)
        assertEquals(64, values.size)
        assertEquals("Tool 1", values.first())
    }

    // --- поведение совпадает со старой реализацией на обычных пакетах ---

    private fun oldValuesOf(packet: String, name: String): List<String> {
        fun decode(s: String) = Regex("\\s+").replace(s, " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&").take(500)
        val esc = Regex.escape(name)
        val result = mutableListOf<String>()
        Regex("(?<![\\w:])" + esc + "\\s*=\\s*\"([^\"]*)\"").findAll(packet)
            .forEach { m -> decode(m.groupValues[1]).takeIf { it.isNotBlank() }?.let { result += it } }
        Regex("<" + esc + "(?:\\s[^>]*)?>(.*?)</" + esc + ">", RegexOption.DOT_MATCHES_ALL).findAll(packet).forEach { m ->
            val text = Regex("<[^>]+>").replace(m.groupValues[1], "\u0000").split('\u0000')
                .map { decode(it).trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            if (text.isNotBlank()) result += text
        }
        return result
    }

    @Test fun sameResultsAsPreviousImplementation() {
        val samples = listOf(
            """<rdf:Description xmp:CreatorTool="Adobe Photoshop 25.0" xmp:CreateDate = "2024-01-01"/>""",
            """<dc:title><rdf:Alt><rdf:li xml:lang="x-default">Закат &amp; море</rdf:li></rdf:Alt></dc:title>""",
            """<dc:creator><rdf:Seq><rdf:li>A</rdf:li><rdf:li>B</rdf:li></rdf:Seq></dc:creator>""",
            """<stEvt:softwareAgent>Adobe Lightroom</stEvt:softwareAgent><stEvt:softwareAgent>GIMP 2.10</stEvt:softwareAgent>""",
            """<x xmp:CreatorTool="one"/><x myxmp:CreatorTool="not-this"/><x a:xmp:CreatorTool="nor-this"/>""",
            """<dc:titleExtra>no</dc:titleExtra><dc:title>yes</dc:title>""",
            """<dc:description xml:lang="en">multi
               line   text</dc:description>""",
            """<exif:GPSLatitude>41,54.168N</exif:GPSLatitude> exif:GPSLongitude="12,29.784E" """,
            """<dc:rights/> <dc:rights>Copyright</dc:rights>""",
        )
        val names = listOf("xmp:CreatorTool", "xmp:CreateDate", "dc:title", "dc:creator", "dc:description",
            "dc:rights", "exif:GPSLatitude", "exif:GPSLongitude", XmpExtractor.KEY_SOFTWARE_AGENT)
        for (sample in samples) for (name in names) {
            assertEquals("$name in $sample", oldValuesOf(sample, name), XmpExtractor.valuesOf(sample, name))
        }
    }
}
