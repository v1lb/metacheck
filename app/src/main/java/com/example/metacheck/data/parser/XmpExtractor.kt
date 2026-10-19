package com.example.metacheck.data.parser

import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.PngTextEntry

data class XmpData(val items: List<MetaItem>, val fields: Map<String, String>)

/**
 * Извлечение и разбор XMP.
 * XML-парсер намеренно не используется: нужные поля берутся регулярными выражениями
 * по пакету ограниченного размера. Так исключены XXE и «миллиард смехов».
 */
object XmpExtractor {
    const val MAX_PACKET = 512 * 1024
    const val KEY_SOFTWARE_AGENT = "stEvt:softwareAgent"
    const val KEY_CREATOR_TOOL = "xmp:CreatorTool"

    private val START = "<x:xmpmeta".ascii()
    private val END = "</x:xmpmeta>".ascii()
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")

    private val known = listOf(
        KEY_CREATOR_TOOL to "Инструмент создания",
        "xmp:CreateDate" to "Дата создания (XMP)",
        "xmp:ModifyDate" to "Дата изменения (XMP)",
        "dc:title" to "Название",
        "dc:creator" to "Автор",
        "dc:description" to "Описание",
        "dc:rights" to "Права",
        "tiff:Make" to "Производитель (XMP)",
        "tiff:Model" to "Модель (XMP)",
        "exif:GPSLatitude" to "GPSLatitude (XMP)",
        "exif:GPSLongitude" to "GPSLongitude (XMP)",
        "exif:GPSAltitude" to "GPSAltitude (XMP)",
        SignalEngine.KEY_DIGITAL_SOURCE_TYPE to "Тип источника (IPTC)",
    )

    fun extractPacket(data: ByteArray, pngText: List<PngTextEntry> = emptyList()): String? {
        pngText.firstOrNull { it.keyword == "XML:com.adobe.xmp" }
            ?.text?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val s = data.indexOfSeq(START)
        if (s < 0) return null
        val e = data.indexOfSeq(END, s, minOf(data.size, s + MAX_PACKET))
        if (e < 0) return null
        return String(data, s, e + END.size - s, Charsets.UTF_8)
    }

    fun parse(packet: String): XmpData {
        val fields = LinkedHashMap<String, String>()
        val items = mutableListOf<MetaItem>()
        for ((name, label) in known) {
            val value = valuesOf(packet, name).firstOrNull() ?: continue
            fields[name] = value
            items += MetaItem(label, value)
        }
        val agents = valuesOf(packet, KEY_SOFTWARE_AGENT).distinct().take(10)
        if (agents.isNotEmpty()) {
            fields[KEY_SOFTWARE_AGENT] = agents.joinToString(", ")
            items += MetaItem("Программы в истории правок", agents.joinToString(", "))
        }
        return XmpData(items, fields)
    }

    private fun valuesOf(packet: String, name: String): List<String> {
        val esc = Regex.escape(name)
        val result = mutableListOf<String>()
        val attr = Regex("(?<![\\w:])" + esc + "\\s*=\\s*\"([^\"]*)\"")
        attr.findAll(packet).forEach { m -> decode(m.groupValues[1]).takeIf { it.isNotBlank() }?.let { result += it } }
        val element = Regex("<" + esc + "(?:\\s[^>]*)?>(.*?)</" + esc + ">", RegexOption.DOT_MATCHES_ALL)
        element.findAll(packet).forEach { m ->
            val text = TAG.replace(m.groupValues[1], "\u0000")
                .split('\u0000')
                .map { decode(it).trim() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
            if (text.isNotBlank()) result += text
        }
        return result
    }

    private fun decode(s: String): String =
        SPACES.replace(s, " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .take(500)
}

object C2paDetector {
    private val JUMB = "jumb".ascii()
    private val C2PA = "c2pa".ascii()

    /** Грубая проверка: есть контейнер JUMBF и метка c2pa. Подпись не проверяется. */
    fun looksPresent(data: ByteArray): Boolean =
        data.indexOfSeq(JUMB) >= 0 && data.indexOfSeq(C2PA) >= 0
}
