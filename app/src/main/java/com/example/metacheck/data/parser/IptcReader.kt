package com.example.metacheck.data.parser

import com.example.metacheck.domain.model.MetaItem
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * IPTC-IIM из JPEG: сегмент APP13 «Photoshop 3.0», ресурс 8BIM 0x0404, записи 2:xx.
 * Здесь лежат ключевые слова, город, страна, заголовок, подпись и автор, которые пишут
 * Lightroom, Photoshop, фотобанки и редакционные системы. Только чтение, без исключений наружу.
 */
object IptcReader {
    const val KEY_PREFIX = "iptc:"

    /** Набор данных записи 2 → имя поля (ключ "iptc:<имя>"). Порядок задаёт порядок показа. */
    private val datasets = linkedMapOf(
        5 to "ObjectName",
        105 to "Headline",
        120 to "Caption",
        25 to "Keywords",
        80 to "Byline",
        116 to "Copyright",
        110 to "Credit",
        115 to "Source",
        92 to "Sublocation",
        90 to "City",
        95 to "State",
        101 to "Country",
    )

    private const val MAX_VALUES = 64
    private const val MAX_VALUE_LENGTH = 2000
    private val photoshop = "Photoshop 3.0\u0000".toByteArray(Charsets.US_ASCII)

    fun read(data: ByteArray): List<MetaItem> = fields(data).map { (name, texts) ->
        MetaItem(name, texts.joinToString(", ").take(MAX_VALUE_LENGTH), KEY_PREFIX + name)
    }

    /** Поля по имени ("Keywords", "City"…) со всеми значениями; повторяемые поля — списком. */
    fun fields(data: ByteArray): Map<String, List<String>> = try {
        findIim(data)?.let(::parseIim).orEmpty()
    } catch (_: RuntimeException) {
        emptyMap()
    }

    /** Ищет APP13 до начала сжатых данных (SOS) и возвращает содержимое ресурса 0x0404. */
    private fun findIim(d: ByteArray): ByteArray? {
        if (d.size < 4 || u8(d, 0) != 0xFF || u8(d, 1) != 0xD8) return null
        var i = 2
        while (i + 4 <= d.size) {
            if (u8(d, i) != 0xFF) return null
            val marker = u8(d, i + 1)
            if (marker == 0xFF) { i++; continue }
            if (marker == 0xDA || marker == 0xD9) return null
            if (marker in 0xD0..0xD7 || marker == 0x01) { i += 2; continue }
            val len = u16(d, i + 2)
            if (len < 2 || i + 2 + len > d.size) return null
            val start = i + 4
            val end = i + 2 + len
            if (marker == 0xED && startsWith(d, start, photoshop)) {
                resource0404(d, start + photoshop.size, end)?.let { return it }
            }
            i = end
        }
        return null
    }

    private fun resource0404(d: ByteArray, from: Int, end: Int): ByteArray? {
        var p = from
        while (p + 12 <= end) {
            if (d[p] != '8'.code.toByte() || d[p + 1] != 'B'.code.toByte() ||
                d[p + 2] != 'I'.code.toByte() || d[p + 3] != 'M'.code.toByte()) return null
            val id = u16(d, p + 4)
            val nameLen = u8(d, p + 6)
            var q = p + 6 + 1 + nameLen
            if ((1 + nameLen) % 2 != 0) q++ // имя (Pascal) дополняется до чётной длины
            if (q + 4 > end) return null
            val size = ((u8(d, q) shl 24) or (u8(d, q + 1) shl 16) or (u8(d, q + 2) shl 8) or u8(d, q + 3))
            q += 4
            if (size < 0 || q + size > end) return null
            if (id == 0x0404) return d.copyOfRange(q, q + size)
            p = q + size + (size and 1)
        }
        return null
    }

    private fun parseIim(d: ByteArray): Map<String, List<String>> {
        var utf8 = false
        val values = LinkedHashMap<Int, MutableList<ByteArray>>()
        var p = 0
        var count = 0
        while (p + 5 <= d.size && count < MAX_VALUES * 4) {
            if (u8(d, p) != 0x1C) break
            val record = u8(d, p + 1)
            val dataset = u8(d, p + 2)
            val len = u16(d, p + 3)
            if (len and 0x8000 != 0) break // расширенная длина: в текстовых полях не встречается
            val start = p + 5
            if (start + len > d.size) break
            val value = d.copyOfRange(start, start + len)
            if (record == 1 && dataset == 90) {
                // Coded Character Set: ESC % G — UTF-8.
                utf8 = value.contentEquals(byteArrayOf(0x1B, 0x25, 0x47))
            } else if (record == 2 && dataset in datasets) {
                values.getOrPut(dataset) { mutableListOf() }.let { if (it.size < MAX_VALUES) it += value }
            }
            count++
            p = start + len
        }
        val result = LinkedHashMap<String, List<String>>()
        for ((dataset, name) in datasets) {
            val texts = values[dataset].orEmpty()
                .map { decode(it, utf8).trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (texts.isNotEmpty()) result[name] = texts
        }
        return result
    }

    /** UTF-8, если так помечено или байты корректны как UTF-8; иначе Latin-1 (старые файлы). */
    private fun decode(bytes: ByteArray, utf8: Boolean): String {
        if (utf8) return String(bytes, Charsets.UTF_8)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun startsWith(d: ByteArray, at: Int, prefix: ByteArray): Boolean {
        if (at + prefix.size > d.size) return false
        for (k in prefix.indices) if (d[at + k] != prefix[k]) return false
        return true
    }

    private fun u8(d: ByteArray, i: Int) = d[i].toInt() and 0xFF
    private fun u16(d: ByteArray, i: Int) = (u8(d, i) shl 8) or u8(d, i + 1)
}
