package com.example.metacheck.data.parser

import com.example.metacheck.domain.model.C2paFacts
import com.example.metacheck.domain.model.ImageFormat

/**
 * Чтение Content Credentials (C2PA) без сети: ищем хранилище манифестов (JUMBF) в файле,
 * разбираем утверждения c2pa.actions и заявку (claim) и достаём:
 *  - digitalSourceType действий: trainedAlgorithmicMedia = создано ИИ, composite… = правки ИИ;
 *  - claim_generator: кто записал манифест (ChatGPT, Adobe Firefly, Google и т. д.);
 *  - softwareAgent действий с ИИ-источником.
 *
 * Где лежит хранилище (спецификация C2PA 2.x, «Embedding manifests»):
 *  JPEG — сегменты APP11 (JUMBF, ISO 19566-5), длинные разбиты на несколько сегментов;
 *  PNG — чанк caBX; WebP — чанк RIFF «C2PA»; HEIC/AVIF — блок uuid с идентификатором C2PA.
 * Если файл другого вида или разметка нестандартная, ищем блок «jumb» прямо в байтах.
 *
 * Подпись и хэши не проверяются: манифест можно скопировать в другой файл. Но случайно метка
 * «создано ИИ» не появляется, поэтому это сильный признак.
 */
object C2paReader {

    private const val MAX_BOX_DEPTH = 16

    /** null — C2PA в файле нет. */
    fun read(data: ByteArray, format: ImageFormat): C2paFacts? {
        val store = when (format) {
            ImageFormat.JPEG -> jpegStore(data)
            ImageFormat.PNG -> pngStore(data)
            ImageFormat.WEBP -> webpStore(data)
            ImageFormat.HEIC, ImageFormat.AVIF -> isoBmffStore(data)
            else -> null
        } ?: scanForStore(data)
        if (store == null) {
            // Похоже на C2PA, но контейнер не нашли (например, обрезан): сообщаем факт без подробностей.
            return if (looksPresent(data)) C2paFacts(parsed = false) else null
        }
        return try {
            parseStore(store)
        } catch (_: Exception) {
            C2paFacts(parsed = false)
        }
    }

    fun looksPresent(data: ByteArray): Boolean =
        data.indexOfSeq(JUMB) >= 0 && data.indexOfSeq(C2PA_LABEL) >= 0

    // --- Поиск хранилища в контейнерах ---

    /** Сегменты APP11 с JUMBF: склеиваем по номеру экземпляра (En) в порядке номера сегмента (Z). */
    internal fun jpegStore(data: ByteArray): ByteArray? {
        if (data.size < 4 || data[0] != 0xFF.toByte() || data[1] != 0xD8.toByte()) return null
        val parts = HashMap<Int, java.util.TreeMap<Long, ByteArray>>()
        var i = 2
        while (i + 4 <= data.size) {
            if (data[i] != 0xFF.toByte()) return null
            val marker = data[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue }
            if (marker == 0xD9 || marker == 0xDA) break // EOI / начало сжатых данных
            if (marker in 0xD0..0xD7 || marker == 0x01) { i += 2; continue }
            val len = u16(data, i + 2)
            if (len < 2 || i + 2 + len > data.size) break
            val start = i + 4
            val segEnd = i + 2 + len
            // CI "JP", En (2 байта), Z (4 байта), дальше данные блока.
            if (marker == 0xEB && segEnd - start >= 8 && data[start] == 'J'.code.toByte() && data[start + 1] == 'P'.code.toByte()) {
                val instance = u16(data, start + 2)
                val seq = u32(data, start + 4)
                var payload = start + 8
                if (seq > 1) {
                    // В продолжениях повторяется заголовок блока (LBox, TBox и, если LBox = 1, XLBox).
                    payload += if (u32(data, payload) == 1L) 16 else 8
                }
                if (payload <= segEnd) {
                    parts.getOrPut(instance) { java.util.TreeMap() }[seq] = data.copyOfRange(payload, segEnd)
                }
            }
            i = segEnd
        }
        for (chunks in parts.values) {
            val joined = java.io.ByteArrayOutputStream().apply { chunks.values.forEach { write(it) } }.toByteArray()
            if (isC2paStore(joined)) return joined
        }
        return null
    }

    internal fun pngStore(data: ByteArray): ByteArray? {
        var i = 8
        while (i + 12 <= data.size) {
            val len = u32(data, i)
            if (len < 0 || len > data.size - i - 12) return null
            val type = String(data, i + 4, 4, Charsets.ISO_8859_1)
            if (type == "caBX") {
                val chunk = data.copyOfRange(i + 8, i + 8 + len.toInt())
                return chunk.takeIf(::isC2paStore)
            }
            if (type == "IEND") return null
            i += 12 + len.toInt()
        }
        return null
    }

    internal fun webpStore(data: ByteArray): ByteArray? {
        if (data.size < 12) return null
        var i = 12
        while (i + 8 <= data.size) {
            val type = String(data, i, 4, Charsets.ISO_8859_1)
            val len = u32le(data, i + 4)
            if (len < 0 || len > data.size - i - 8) return null
            if (type == "C2PA") return data.copyOfRange(i + 8, i + 8 + len.toInt()).takeIf(::isC2paStore)
            i += 8 + len.toInt() + (len.toInt() and 1)
        }
        return null
    }

    /** Верхнеуровневые блоки ISO BMFF: uuid C2PA → версия/флаги, назначение, смещение, JUMBF. */
    internal fun isoBmffStore(data: ByteArray): ByteArray? {
        var i = 0
        while (i + 8 <= data.size) {
            var size = u32(data, i)
            val type = String(data, i + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (i + 16 > data.size) return null
                size = u64(data, i + 8)
                header = 16
            } else if (size == 0L) {
                size = (data.size - i).toLong()
            }
            if (size < header || size > data.size - i) return null
            if (type == "uuid" && size >= header + 16 + 4 && matches(data, i + header, C2PA_UUID)) {
                var p = i + header + 16 + 4 // UUID, версия и флаги
                val boxEnd = i + size.toInt()
                val purposeStart = p
                while (p < boxEnd && data[p] != 0.toByte()) p++
                val purpose = String(data, purposeStart, p - purposeStart, Charsets.UTF_8)
                p++
                if (purpose == "manifest") p += 8 // смещение для Merkle-дерева
                if (p < boxEnd) return data.copyOfRange(p, boxEnd).takeIf(::isC2paStore)
            }
            i += size.toInt()
        }
        return null
    }

    /** Запасной путь: первый блок «jumb», внутри которого описание с меткой c2pa. */
    internal fun scanForStore(data: ByteArray): ByteArray? {
        var from = 0
        repeat(64) {
            val at = data.indexOfSeq(JUMB, from)
            if (at < 4) return null
            val start = at - 4
            val len = u32(data, start)
            if (len in 16..(data.size - start).toLong()) {
                val candidate = data.copyOfRange(start, start + len.toInt())
                if (isC2paStore(candidate)) return candidate
            }
            from = at + 4
        }
        return null
    }

    private fun isC2paStore(box: ByteArray): Boolean =
        runCatching { boxes(box, 0, box.size).firstOrNull()?.let { it.type == "jumb" && label(box, it) == "c2pa" } }
            .getOrNull() == true

    // --- JUMBF ---

    private class Box(val type: String, val start: Int, val end: Int)

    private fun boxes(data: ByteArray, from: Int, to: Int): List<Box> {
        val out = ArrayList<Box>()
        var i = from
        while (i + 8 <= to) {
            var size = u32(data, i)
            var header = 8
            if (size == 1L) {
                if (i + 16 > to) break
                size = u64(data, i + 8)
                header = 16
            } else if (size == 0L) {
                size = (to - i).toLong()
            }
            if (size < header || size > to - i) break
            out += Box(String(data, i + 4, 4, Charsets.ISO_8859_1), i + header, i + size.toInt())
            i += size.toInt()
        }
        return out
    }

    /** Метка из блока описания jumd: 16 байт типа, байт флагов, затем строка с нулём в конце. */
    private fun label(data: ByteArray, superbox: Box): String? {
        val desc = boxes(data, superbox.start, superbox.end).firstOrNull() ?: return null
        if (desc.type != "jumd" || desc.end - desc.start < 17) return null
        val toggles = data[desc.start + 16].toInt()
        if (toggles and 0x02 == 0) return null
        var p = desc.start + 17
        val s = p
        while (p < desc.end && data[p] != 0.toByte()) p++
        return String(data, s, p - s, Charsets.UTF_8)
    }

    private class Node(val label: String?, val children: List<Node>, val cbor: List<Any?>)

    private fun tree(data: ByteArray, box: Box, depth: Int): Node {
        if (depth > MAX_BOX_DEPTH) return Node(null, emptyList(), emptyList())
        val inner = boxes(data, box.start, box.end)
        val children = ArrayList<Node>()
        val cbor = ArrayList<Any?>()
        for (b in inner.drop(1)) {
            when (b.type) {
                "jumb" -> children += tree(data, b, depth + 1)
                "cbor" -> cbor += runCatching { Cbor.decode(data, b.start, b.end - b.start) }.getOrNull()
            }
        }
        return Node(label(data, box), children, cbor)
    }

    // --- Разбор манифестов ---

    internal fun parseStore(store: ByteArray): C2paFacts {
        val root = boxes(store, 0, store.size).first()
        val manifests = tree(store, root, 0).children
        if (manifests.isEmpty()) return C2paFacts(parsed = false)

        val sourceTypes = LinkedHashSet<String>()
        val aiAgents = ArrayList<String>()
        for (manifest in manifests) {
            val assertions = manifest.children.filter { it.label == "c2pa.assertions" }.flatMap { it.children }
            for (assertion in assertions) {
                if (assertion.label?.startsWith("c2pa.actions") != true) continue
                for (map in assertion.cbor.filterIsInstance<Map<*, *>>()) {
                    for (action in (map["actions"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()) {
                        val type = (action["digitalSourceType"] as? String)?.substringAfterLast('/')?.trim()
                        if (type.isNullOrEmpty()) continue
                        sourceTypes += type
                        if (type.contains("AlgorithmicMedia", ignoreCase = true)) {
                            agentName(action["softwareAgent"])?.let { if (it !in aiAgents) aiAgents += it }
                        }
                    }
                }
            }
        }
        // Активный манифест — последний в хранилище.
        val claim = manifests.last().children.firstOrNull { it.label?.startsWith("c2pa.claim") == true }
            ?.cbor?.filterIsInstance<Map<*, *>>()?.firstOrNull()
        return C2paFacts(
            parsed = true,
            generator = claim?.let(::generatorName),
            sourceTypes = sourceTypes,
            aiAgents = aiAgents.take(5),
        )
    }

    /** claim_generator_info (v2: словарь, v1: массив словарей) или старое поле claim_generator. */
    private fun generatorName(claim: Map<*, *>): String? {
        val info = when (val v = claim["claim_generator_info"]) {
            is Map<*, *> -> v
            is List<*> -> v.firstOrNull() as? Map<*, *>
            else -> null
        }
        val fromInfo = info?.let { agentName(it) }
        val legacy = (claim["claim_generator"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return (fromInfo ?: legacy)?.take(MAX_NAME)
    }

    private fun agentName(value: Any?): String? = when (value) {
        is String -> value
        is Map<*, *> -> listOfNotNull(value["name"] as? String, value["version"] as? String).joinToString(" ")
        else -> null
    }?.replace(Regex("[\\p{Cntrl}]"), " ")?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }

    // --- Байты ---

    private const val MAX_NAME = 80
    private val JUMB = "jumb".ascii()
    private val C2PA_LABEL = "c2pa".ascii()
    private val C2PA_UUID = byteArrayOf(
        0xD8.toByte(), 0xFE.toByte(), 0xC3.toByte(), 0xD6.toByte(), 0x1B, 0x0E, 0x48, 0x3C,
        0x92.toByte(), 0x97.toByte(), 0x58, 0x28, 0x87.toByte(), 0x7E, 0xC4.toByte(), 0x81.toByte(),
    )

    private fun matches(data: ByteArray, at: Int, pattern: ByteArray): Boolean =
        at + pattern.size <= data.size && pattern.indices.all { data[at + it] == pattern[it] }

    private fun u16(d: ByteArray, i: Int) = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
    private fun u32(d: ByteArray, i: Int): Long =
        if (i + 4 > d.size) -1 else ((d[i].toLong() and 0xFF) shl 24) or ((d[i + 1].toLong() and 0xFF) shl 16) or
            ((d[i + 2].toLong() and 0xFF) shl 8) or (d[i + 3].toLong() and 0xFF)
    private fun u32le(d: ByteArray, i: Int): Long =
        if (i + 4 > d.size) -1 else ((d[i + 3].toLong() and 0xFF) shl 24) or ((d[i + 2].toLong() and 0xFF) shl 16) or
            ((d[i + 1].toLong() and 0xFF) shl 8) or (d[i].toLong() and 0xFF)
    private fun u64(d: ByteArray, i: Int): Long = (u32(d, i) shl 32) or u32(d, i + 4)
}
