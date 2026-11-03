package com.example.metacheck.data.parser

import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.PngTextEntry

data class XmpData(val items: List<MetaItem>, val fields: Map<String, String>)

/**
 * Извлечение и разбор XMP.
 * XML-парсер намеренно не используется (исключены XXE и «миллиард смехов»).
 *
 * Аудит, п. 11: раньше поля искались регулярными выражениями вида `<tag(...)>(.*?)</tag>` по пакету
 * до 512 КБ. Файл с десятками тысяч открывающих тегов без закрывающих давал O(n²): анализ одной
 * картинки занимал минуты, а регулярное выражение не реагирует на отмену корутины.
 * Теперь разбор линейный: поиск через indexOf, каждое значение ищется в ограниченном окне,
 * число совпадений на поле ограничено.
 */
object XmpExtractor {
    const val MAX_PACKET = 512 * 1024
    const val KEY_SOFTWARE_AGENT = "stEvt:softwareAgent"
    const val KEY_CREATOR_TOOL = "xmp:CreatorTool"

    private val START = "<x:xmpmeta".ascii()
    private val END = "</x:xmpmeta>".ascii()
    private val SPACES = Regex("\\s+")

    /** Сколько символов просматривать в поисках закрывающей кавычки атрибута. */
    private const val MAX_ATTR_SCAN = 4 * 1024
    /** Сколько символов просматривать в поисках закрывающего тега элемента. */
    private const val MAX_ELEMENT_SCAN = 16 * 1024
    /** Сколько совпадений одного поля разбирать (историй правок больше 10 не показываем). */
    private const val MAX_MATCHES_PER_NAME = 64
    /**
     * Сколько вхождений имени проверять, включая неудачные (без '=' или без закрывающего тега).
     * Вместе с ограниченными окнами это даёт жёсткую верхнюю границу работы на поле.
     */
    private const val MAX_ATTEMPTS_PER_NAME = 1024

    /** Ключ поля XMP: "xmp:" + полное имя свойства (например, "xmp:dc:title"). Подпись — из ресурсов. */
    const val KEY_PREFIX = "xmp:"
    /** Ключ сводного поля «программы в истории правок» (все stEvt:softwareAgent). */
    const val KEY_HISTORY_AGENTS = "xmp:history_agents"

    /** Порядок показа известных свойств. */
    private val known = listOf(
        KEY_CREATOR_TOOL,
        "xmp:CreateDate",
        "xmp:ModifyDate",
        "dc:title",
        "dc:creator",
        "dc:description",
        "dc:rights",
        "tiff:Make",
        "tiff:Model",
        "exif:GPSLatitude",
        "exif:GPSLongitude",
        "exif:GPSAltitude",
        SignalEngine.KEY_DIGITAL_SOURCE_TYPE,
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
        for (name in known) {
            val value = valuesOf(packet, name).firstOrNull() ?: continue
            fields[name] = value
            items += MetaItem(name, value, KEY_PREFIX + name)
        }
        val agents = valuesOf(packet, KEY_SOFTWARE_AGENT).distinct().take(10)
        if (agents.isNotEmpty()) {
            fields[KEY_SOFTWARE_AGENT] = agents.joinToString(", ")
            items += MetaItem(KEY_SOFTWARE_AGENT, agents.joinToString(", "), KEY_HISTORY_AGENTS)
        }
        return XmpData(items, fields)
    }

    internal fun valuesOf(packet: String, name: String): List<String> {
        val result = mutableListOf<String>()
        attributeValues(packet, name, result)
        elementValues(packet, name, result)
        return result
    }

    /** name="value": перед именем не буква/цифра/_/двоеточие, вокруг '=' допускаются пробелы. */
    private fun attributeValues(packet: String, name: String, out: MutableList<String>) {
        var from = 0
        var matches = 0
        var attempts = 0
        while (matches < MAX_MATCHES_PER_NAME && attempts++ < MAX_ATTEMPTS_PER_NAME) {
            val at = packet.indexOf(name, from)
            if (at < 0) return
            from = at + name.length
            if (at > 0 && isNameChar(packet[at - 1])) continue
            var i = skipSpaces(packet, from)
            if (i >= packet.length || packet[i] != '=') continue
            i = skipSpaces(packet, i + 1)
            if (i >= packet.length || packet[i] != '"') continue
            val start = i + 1
            val end = indexOfIn(packet, '"', start, minOf(packet.length, start + MAX_ATTR_SCAN)).takeIf { it >= 0 } ?: continue
            matches++
            decode(packet.substring(start, end)).takeIf { it.isNotBlank() }?.let { out += it }
            from = end + 1
        }
    }

    /** <name ...>текст</name>: вложенные теги заменяются разделителем, тексты склеиваются через ", ". */
    private fun elementValues(packet: String, name: String, out: MutableList<String>) {
        val open = "<$name"
        val close = "</$name>"
        var from = 0
        var matches = 0
        var attempts = 0
        while (matches < MAX_MATCHES_PER_NAME && attempts++ < MAX_ATTEMPTS_PER_NAME) {
            val at = packet.indexOf(open, from)
            if (at < 0) return
            from = at + open.length
            val next = packet.getOrNull(from) ?: return
            if (next != '>' && !next.isWhitespace()) continue // это другое имя, например <dc:titleX>
            val windowEnd = minOf(packet.length, from + MAX_ELEMENT_SCAN)
            val tagEnd = indexOfIn(packet, '>', from, windowEnd)
            if (tagEnd < 0 || packet[tagEnd - 1] == '/') continue // нет конца тега или <name/>
            val closeAt = indexOfIn(packet, close, tagEnd + 1, windowEnd)
            if (closeAt < 0) continue
            matches++
            val text = innerTexts(packet, tagEnd + 1, closeAt)
            if (text.isNotBlank()) out += text
            from = closeAt + close.length
        }
    }

    /** Тексты между тегами в [start, end): линейный проход без регулярных выражений. */
    private fun innerTexts(s: String, start: Int, end: Int): String {
        val parts = ArrayList<String>()
        var i = start
        var chunk = start
        while (i < end) {
            if (s[i] == '<') {
                if (i > chunk) parts += s.substring(chunk, i)
                val gt = indexOfIn(s, '>', i + 1, end)
                if (gt < 0) { chunk = end; break }
                i = gt + 1
                chunk = i
            } else {
                i++
            }
        }
        if (chunk < end) parts += s.substring(chunk, end)
        return parts.map { decode(it).trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    }

    private fun isNameChar(c: Char): Boolean = c == ':' || c == '_' || c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private fun skipSpaces(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun indexOfIn(s: String, c: Char, from: Int, endExclusive: Int): Int {
        for (i in from until endExclusive) if (s[i] == c) return i
        return -1
    }

    /** Поиск строки только внутри окна [from, endExclusive), без просмотра остатка пакета. */
    private fun indexOfIn(s: String, needle: String, from: Int, endExclusive: Int): Int {
        val last = endExclusive - needle.length
        var i = from
        while (i <= last) {
            if (s[i] == needle[0] && s.regionMatches(i, needle, 0, needle.length)) return i
            i++
        }
        return -1
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
