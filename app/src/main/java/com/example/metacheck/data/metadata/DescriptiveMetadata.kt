package com.example.metacheck.data.metadata

import com.example.metacheck.data.parser.IptcReader
import com.example.metacheck.data.parser.XmpExtractor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Описательные поля, которые пользователь заполнял сам: ключевые слова, название, подпись,
 * автор, права, кредит, источник и названия мест.
 *
 * При правке в студии блоки XMP и IPTC целиком удаляются: в них могут остаться старые координаты,
 * даты и история правок. Чтобы при этом не пропадали ключевые слова (частая жалоба на редакторы EXIF),
 * описательные поля читаются заранее и записываются обратно в новые, чистые XMP и IPTC —
 * без координат, дат, истории правок и идентификаторов документа.
 */
internal data class DescriptiveMetadata(
    val keywords: List<String> = emptyList(),
    val title: String? = null,
    val headline: String? = null,
    val caption: String? = null,
    val byline: String? = null,
    val copyright: String? = null,
    val credit: String? = null,
    val source: String? = null,
    val sublocation: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
) {
    val isEmpty: Boolean get() = this == DescriptiveMetadata()

    /** Без названий мест: когда пользователь удаляет или меняет координаты. */
    fun withoutPlaces() = copy(sublocation = null, city = null, state = null, country = null)

    /** Segment payloads to insert after EXIF: APP1 XMP and APP13 IPTC. */
    fun segments(): List<Pair<Int, ByteArray>> = if (isEmpty) emptyList() else listOf(0xE1 to xmpSegment(), 0xED to iptcSegment())

    private fun xmpSegment(): ByteArray {
        val body = StringBuilder()
        fun alt(name: String, value: String?) = value?.let {
            body.append("<$name><rdf:Alt><rdf:li xml:lang=\"x-default\">${esc(it)}</rdf:li></rdf:Alt></$name>")
        }
        fun simple(name: String, value: String?) = value?.let { body.append("<$name>${esc(it)}</$name>") }
        if (keywords.isNotEmpty()) {
            body.append("<dc:subject><rdf:Bag>")
            keywords.forEach { body.append("<rdf:li>${esc(it)}</rdf:li>") }
            body.append("</rdf:Bag></dc:subject>")
        }
        alt("dc:title", title)
        alt("dc:description", caption)
        byline?.let { body.append("<dc:creator><rdf:Seq><rdf:li>${esc(it)}</rdf:li></rdf:Seq></dc:creator>") }
        alt("dc:rights", copyright)
        simple("photoshop:Headline", headline)
        simple("photoshop:Credit", credit)
        simple("photoshop:Source", source)
        simple("Iptc4xmpCore:Location", sublocation)
        simple("photoshop:City", city)
        simple("photoshop:State", state)
        simple("photoshop:Country", country)
        val packet = "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
            " xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\"" +
            " xmlns:Iptc4xmpCore=\"http://iptc.org/std/Iptc4xmpCore/1.0/xmlns/\">" +
            body + "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>"
        return "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII) + packet.toByteArray(Charsets.UTF_8)
    }

    private fun iptcSegment(): ByteArray {
        val iim = ByteArrayOutputStream()
        fun put(record: Int, dataset: Int, value: ByteArray) {
            iim.write(0x1C); iim.write(record); iim.write(dataset)
            iim.write(value.size shr 8); iim.write(value.size and 0xFF); iim.write(value)
        }
        fun text(dataset: Int, value: String?, limit: Int) = value?.let { put(2, dataset, utf8Limited(it, limit)) }
        put(1, 90, byteArrayOf(0x1B, 0x25, 0x47)) // UTF-8
        put(2, 0, byteArrayOf(0, 4)) // Record version
        text(5, title, 64)
        text(105, headline, 256)
        text(120, caption, 2000)
        keywords.forEach { text(25, it, 64) }
        text(80, byline, 32)
        text(116, copyright, 128)
        text(110, credit, 32)
        text(115, source, 32)
        text(92, sublocation, 32)
        text(90, city, 32)
        text(95, state, 32)
        text(101, country, 64)
        val data = iim.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("Photoshop 3.0\u0000".toByteArray(Charsets.US_ASCII))
        out.write("8BIM".toByteArray(Charsets.US_ASCII)); out.write(0x04); out.write(0x04)
        out.write(0); out.write(0) // пустое имя ресурса
        out.write(data.size ushr 24); out.write(data.size ushr 16 and 0xFF); out.write(data.size ushr 8 and 0xFF); out.write(data.size and 0xFF)
        out.write(data)
        if (data.size % 2 == 1) out.write(0)
        return out.toByteArray()
    }

    companion object {
        private const val MAX_KEYWORDS = 64
        private const val MAX_TEXT = 2000
        /** Хватает, чтобы найти XMP и APP13 в начале JPEG. */
        private const val HEADER_BYTES = 2 * 1024 * 1024

        fun read(jpeg: File): DescriptiveMetadata = try {
            val head = FileInputStream(jpeg).use { input ->
                val buffer = ByteArray(minOf(jpeg.length(), HEADER_BYTES.toLong()).toInt())
                var n = 0
                while (n < buffer.size) { val r = input.read(buffer, n, buffer.size - n); if (r <= 0) break; n += r }
                buffer.copyOf(n)
            }
            from(XmpExtractor.extractPacket(head)?.let { XmpExtractor.parse(it).fields }.orEmpty(), IptcReader.fields(head))
        } catch (_: IOException) {
            DescriptiveMetadata()
        } catch (_: RuntimeException) {
            DescriptiveMetadata()
        }

        /** XMP важнее IPTC: современные программы обновляют XMP, а IIM часто остаётся старым. */
        internal fun from(xmp: Map<String, String>, iptc: Map<String, List<String>>): DescriptiveMetadata {
            fun clean(v: String?) = v?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
            fun pick(xmpKey: String, iptcKey: String) = clean(xmp[xmpKey]) ?: clean(iptc[iptcKey]?.firstOrNull())
            val keywords = (iptc["Keywords"].orEmpty() + xmp["dc:subject"].orEmpty().split(", "))
                .map { it.trim() }.filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .take(MAX_KEYWORDS)
            return DescriptiveMetadata(
                keywords = keywords,
                title = pick("dc:title", "ObjectName"),
                headline = pick("photoshop:Headline", "Headline"),
                caption = pick("dc:description", "Caption"),
                byline = pick("dc:creator", "Byline"),
                copyright = pick("dc:rights", "Copyright"),
                credit = pick("photoshop:Credit", "Credit"),
                source = pick("photoshop:Source", "Source"),
                sublocation = pick("Iptc4xmpCore:Location", "Sublocation"),
                city = pick("photoshop:City", "City"),
                state = pick("photoshop:State", "State"),
                country = pick("photoshop:Country", "Country"),
            )
        }

        private fun esc(s: String) = buildString(s.length) {
            for (c in s) when (c) {
                '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;")
                else -> if (c >= ' ' || c == '\n' || c == '\t') append(c)
            }
        }

        /** Обрезка по байтам UTF-8 без разрыва символа. */
        private fun utf8Limited(s: String, limit: Int): ByteArray {
            var bytes = s.toByteArray(Charsets.UTF_8)
            if (bytes.size <= limit) return bytes
            var end = limit
            while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
            bytes = bytes.copyOf(end)
            return bytes
        }
    }
}
