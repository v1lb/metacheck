package com.example.metacheck.data.parser

import com.example.metacheck.domain.model.PngTextEntry
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/**
 * Безопасный разбор текстовых блоков PNG (tEXt, zTXt, iTXt).
 *
 * Защита от Malformed Metadata Exploit:
 *  - длина блока читается как беззнаковая и проверяется по размеру буфера;
 *  - число блоков, число записей и размер текста ограничены;
 *  - сжатые блоки распаковываются с жёстким лимитом выхода (защита от zip-бомбы);
 *  - любая ошибка формата ведёт к остановке разбора, а не к исключению.
 */
object PngTextParser {
    const val MAX_CHUNKS = 5_000
    const val MAX_TEXT_CHUNK_BYTES = 1 shl 20
    const val MAX_INFLATED_BYTES = 1 shl 20
    const val MAX_ENTRIES = 64
    const val MAX_TEXT_CHARS = 20_000
    const val BAD_DATA = "(данные повреждены или превышают лимит)"

    fun parse(data: ByteArray): List<PngTextEntry> {
        if (!data.startsWith(PNG_SIGNATURE)) return emptyList()
        val out = ArrayList<PngTextEntry>()
        var pos = 8L
        var chunks = 0
        while (chunks < MAX_CHUNKS && out.size < MAX_ENTRIES && pos + 12 <= data.size) {
            val p = pos.toInt()
            val len = readUInt32(data, p)
            val type = chunkType(data, p + 4) ?: break
            val bodyStart = p + 8
            val bodyEnd = bodyStart.toLong() + len
            if (bodyEnd + 4 > data.size) break
            if (type == "IEND") break
            if ((type == "tEXt" || type == "zTXt" || type == "iTXt") && len <= MAX_TEXT_CHUNK_BYTES) {
                decode(type, data, bodyStart, bodyEnd.toInt())?.let { out += it }
            }
            pos = bodyEnd + 4
            chunks++
        }
        return out
    }

    private fun readUInt32(d: ByteArray, o: Int): Long =
        ((d[o].toLong() and 0xFF) shl 24) or
            ((d[o + 1].toLong() and 0xFF) shl 16) or
            ((d[o + 2].toLong() and 0xFF) shl 8) or
            (d[o + 3].toLong() and 0xFF)

    private fun chunkType(d: ByteArray, o: Int): String? {
        for (i in 0 until 4) {
            val c = d[o + i].toInt()
            val letter = (c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code)
            if (!letter) return null
        }
        return String(d, o, 4, Charsets.ISO_8859_1)
    }

    private fun indexOfZero(d: ByteArray, from: Int, to: Int): Int {
        for (i in from until to) if (d[i] == 0.toByte()) return i
        return -1
    }

    private fun decode(type: String, d: ByteArray, start: Int, end: Int): PngTextEntry? {
        val nul = indexOfZero(d, start, end)
        if (nul < 0 || nul == start || nul - start > 79) return null
        val keyword = String(d, start, nul - start, Charsets.ISO_8859_1)
        val rest = nul + 1
        return when (type) {
            "tEXt" -> PngTextEntry(keyword, clean(String(d, rest, end - rest, Charsets.ISO_8859_1)))
            "zTXt" -> {
                if (rest >= end || d[rest].toInt() != 0) return null
                val raw = inflateBounded(d, rest + 1, end - rest - 1, MAX_INFLATED_BYTES)
                PngTextEntry(keyword, if (raw == null) BAD_DATA else clean(String(raw, Charsets.ISO_8859_1)))
            }
            else -> decodeItxt(keyword, d, rest, end)
        }
    }

    private fun decodeItxt(keyword: String, d: ByteArray, rest: Int, end: Int): PngTextEntry? {
        if (rest + 2 > end) return null
        val compressed = d[rest].toInt()
        val method = d[rest + 1].toInt()
        val langEnd = indexOfZero(d, rest + 2, end)
        if (langEnd < 0) return null
        val translatedEnd = indexOfZero(d, langEnd + 1, end)
        if (translatedEnd < 0) return null
        val textStart = translatedEnd + 1
        return when (compressed) {
            0 -> PngTextEntry(keyword, clean(String(d, textStart, end - textStart, Charsets.UTF_8)))
            1 -> {
                if (method != 0) return null
                val raw = inflateBounded(d, textStart, end - textStart, MAX_INFLATED_BYTES)
                PngTextEntry(keyword, if (raw == null) BAD_DATA else clean(String(raw, Charsets.UTF_8)))
            }
            else -> null
        }
    }

    private fun clean(s: String): String =
        s.filter { it == '\n' || it == '\t' || !it.isISOControl() }.take(MAX_TEXT_CHARS)
}

/** Распаковка zlib с жёстким лимитом размера результата. null — данные повреждены или больше лимита. */
internal fun inflateBounded(src: ByteArray, off: Int, len: Int, max: Int): ByteArray? {
    if (len < 0 || off < 0 || off + len > src.size) return null
    val inflater = Inflater()
    return try {
        inflater.setInput(src, off, len)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0) break
            if (out.size() + n > max) return null
            out.write(buf, 0, n)
        }
        if (inflater.finished()) out.toByteArray() else null
    } catch (e: DataFormatException) {
        null
    } finally {
        inflater.end()
    }
}
