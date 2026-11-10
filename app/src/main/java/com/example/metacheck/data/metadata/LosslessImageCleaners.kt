package com.example.metacheck.data.metadata

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Очистка WebP без пережатия: из контейнера RIFF удаляются блоки EXIF, XMP и все неизвестные
 * (в них бывают C2PA и метки редакторов). Пиксели (VP8/VP8L/ALPH/анимация) копируются байт в байт.
 */
object WebpCleaner {
    /** Блоки, нужные для показа картинки. Всё остальное удаляется. */
    private val imageChunks = setOf("VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP")
    private const val FLAG_EXIF = 0x08
    private const val FLAG_XMP = 0x04

    fun strip(source: File, output: File) {
        val d = source.readBytes()
        if (!isWebp(d)) throw IOException("Not a WebP file")
        val out = java.io.ByteArrayOutputStream(d.size)
        out.write(d, 0, 12)
        var p = 12
        var kept = 0
        while (p + 8 <= d.size) {
            val type = String(d, p, 4, Charsets.ISO_8859_1)
            val size = le32(d, p + 4)
            val end = p + 8 + size
            if (size < 0 || end > d.size) throw IOException("Truncated WebP chunk")
            val padded = minOf(d.size, end + (size and 1))
            if (type in imageChunks) {
                val chunk = d.copyOfRange(p, padded)
                if (type == "VP8X" && size >= 1) chunk[8] = (chunk[8].toInt() and (FLAG_EXIF or FLAG_XMP).inv()).toByte()
                out.write(chunk)
                if (type != "VP8X" && type != "ICCP") kept++
            }
            p = padded
        }
        if (kept == 0) throw IOException("No image data in WebP")
        val result = out.toByteArray()
        putLe32(result, 4, result.size - 8)
        output.writeBytes(result)
    }

    /** true, если в файле остались только блоки изображения и флаги EXIF/XMP сброшены. */
    fun isClean(file: File): Boolean = runCatching {
        val d = file.readBytes()
        if (!isWebp(d)) return false
        var p = 12
        while (p + 8 <= d.size) {
            val type = String(d, p, 4, Charsets.ISO_8859_1)
            val size = le32(d, p + 4)
            if (size < 0 || type !in imageChunks) return false
            if (type == "VP8X" && (d[p + 8].toInt() and (FLAG_EXIF or FLAG_XMP)) != 0) return false
            p += 8 + size + (size and 1)
        }
        p >= d.size
    }.getOrDefault(false)

    private fun isWebp(d: ByteArray) = d.size >= 20 &&
        String(d, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(d, 8, 4, Charsets.ISO_8859_1) == "WEBP"

    private fun le32(d: ByteArray, i: Int) =
        (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8) or ((d[i + 2].toInt() and 0xFF) shl 16) or ((d[i + 3].toInt() and 0xFF) shl 24)

    private fun putLe32(d: ByteArray, i: Int, v: Int) {
        d[i] = v.toByte(); d[i + 1] = (v shr 8).toByte(); d[i + 2] = (v shr 16).toByte(); d[i + 3] = (v shr 24).toByte()
    }
}

/**
 * Очистка HEIC/HEIF без пережатия. Удалять элементы из контейнера нельзя без пересчёта всех смещений,
 * поэтому содержимое метаданных затирается на месте, размер файла не меняется:
 * элемент Exif — пустым TIFF без тегов, элемент XMP — пустым пакетом и пробелами, прочие служебные
 * элементы (mime, uri) — нулями. Верхнеуровневые блоки, не нужные для показа фото (uuid с C2PA и метками
 * производителей, mpvd — видео «живого фото» Samsung), превращаются в free с нулями.
 * Фото, миниатюры, карты глубины и HDR остаются как были.
 */
object HeifCleaner {
    /** Пустой Exif: смещение TIFF-заголовка 0, затем TIFF «MM» с одной пустой IFD. */
    private val emptyExif = byteArrayOf(0, 0, 0, 0, 0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0)
    private val emptyXmp = "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?><x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/><?xpacket end=\"w\"?>"
        .toByteArray(Charsets.US_ASCII)

    /** Блоки верхнего уровня, которые остаются как есть. */
    private val keptBoxes = setOf("ftyp", "meta", "mdat", "free", "skip", "wide")
    /** Последовательности кадров (как видео): их структуру этот код не разбирает. */
    private val sequenceBoxes = setOf("moov", "moof", "mfra")
    private const val MAX_METADATA_BYTES = 16L * 1024 * 1024

    private data class Box(val type: String, val start: Long, val header: Int, val end: Long) {
        val dataStart get() = start + header
    }

    private data class Extent(val offset: Long, val length: Long)
    private data class Item(val id: Long, val type: String, val contentType: String?) {
        val isExif get() = type == "Exif"
        val isXmp get() = type == "mime" && contentType?.lowercase()?.contains("rdf+xml") == true
        /** Всё, что не изображение: Exif, XMP и любые вложенные данные (кроме картинок) и ссылки. */
        val isMetadata get() = isExif || type == "uri " ||
            (type == "mime" && contentType?.lowercase()?.startsWith("image/") != true)
    }

    private class Target(val extent: Extent, val bytes: ByteArray)
    private class Plan(val targets: List<Target>, val removable: List<Box>)

    /** Копирует [source] в [output] и затирает метаданные. Бросает IOException, если структура непонятна. */
    fun strip(source: File, output: File) {
        source.copyTo(output, overwrite = true)
        RandomAccessFile(output, "rw").use { f ->
            val plan = plan(f)
            for (target in plan.targets) {
                f.seek(target.extent.offset)
                f.write(target.bytes)
            }
            for (box in plan.removable) {
                f.seek(box.start + 4); f.write("free".toByteArray(Charsets.US_ASCII))
                zero(f, box.dataStart, box.end)
            }
        }
        if (!isClean(output)) throw IOException("HEIF metadata still present")
    }

    /** true, если все служебные элементы пустые и лишних блоков верхнего уровня нет. */
    fun isClean(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val plan = plan(f)
            plan.removable.isEmpty() && plan.targets.all { t ->
                val now = ByteArray(t.bytes.size)
                f.seek(t.extent.offset); f.readFully(now)
                now.contentEquals(t.bytes)
            }
        }
    }.getOrDefault(false)

    private fun plan(f: RandomAccessFile): Plan = try {
        buildPlan(f)
    } catch (e: RuntimeException) {
        // Битый файл: выход за границы массива и т. п. Для вызывающего это «формат не разобран».
        throw IOException("Malformed HEIF", e)
    }

    private fun buildPlan(f: RandomAccessFile): Plan {
        val length = f.length()
        val top = topLevel(f)
        if (top.firstOrNull()?.type != "ftyp") throw IOException("Not an ISO BMFF file")
        if (top.any { it.type in sequenceBoxes }) throw IOException("HEIF image sequence")
        val meta = top.firstOrNull { it.type == "meta" } ?: throw IOException("No meta box")
        if (meta.end - meta.start > MAX_METADATA_BYTES) throw IOException("meta box too large")
        val metaBytes = ByteArray((meta.end - meta.dataStart).toInt())
        f.seek(meta.dataStart); f.readFully(metaBytes)
        val children = boxes(metaBytes, 4, metaBytes.size) // FullBox: 4 байта версии и флагов
        val items = children.firstOrNull { it.type == "iinf" }?.let { items(metaBytes, it) }.orEmpty()
        val iloc = children.firstOrNull { it.type == "iloc" } ?: throw IOException("No iloc")
        val idatStart = children.firstOrNull { it.type == "idat" }?.let { meta.dataStart + it.dataStart }
        val locations = locations(metaBytes, iloc, idatStart)

        val metadataIds = items.filter { it.isMetadata }.map { it.id }.toSet()
        // Данные изображений: их нельзя ни затирать, ни прятать в free. Длина 0 значит «до конца файла».
        val imageRanges = locations.filterKeys { it !in metadataIds }.values.flatten()
            .map { it.offset to if (it.length <= 0) length else it.offset + it.length }
        fun touchesImage(from: Long, to: Long) = imageRanges.any { (a, b) -> from < b && a < to }

        val targets = ArrayList<Target>()
        for (item in items) {
            if (!item.isMetadata) continue
            val extents = locations[item.id] ?: continue // элемент без данных
            if (extents.any { it.length <= 0 }) throw IOException("Metadata extent length unknown")
            if (extents.any { it.offset < 0 || it.offset + it.length > length }) throw IOException("Metadata extent outside the file")
            if (extents.any { touchesImage(it.offset, it.offset + it.length) }) throw IOException("Metadata shares bytes with image data")
            val total = extents.sumOf { it.length }
            if (total > MAX_METADATA_BYTES) throw IOException("Metadata item too large")
            val fill = ByteArray(total.toInt())
            when {
                item.isExif -> if (fill.size >= emptyExif.size) emptyExif.copyInto(fill)
                item.isXmp -> { fill.fill(0x20); if (fill.size >= emptyXmp.size) emptyXmp.copyInto(fill) }
            }
            var pos = 0
            for (e in extents) {
                val len = e.length.toInt()
                targets += Target(e, fill.copyOfRange(pos, pos + len))
                pos += len
            }
        }
        val removable = top.filter { it.type !in keptBoxes && !touchesImage(it.start, it.end) }
        return Plan(targets, removable)
    }

    private fun items(d: ByteArray, iinf: Box): List<Item> {
        val s = iinf.dataStart.toInt()
        val version = d[s].toInt() and 0xFF
        val first = s + 4 + if (version == 0) 2 else 4
        return boxes(d, first, iinf.end.toInt()).filter { it.type == "infe" }.map { infe ->
            val p = infe.dataStart.toInt()
            val v = d[p].toInt() and 0xFF
            val end = infe.end.toInt()
            var q = p + 4
            fun cString(): String {
                val start = q
                while (q < end && d[q].toInt() != 0) q++
                return String(d, start, q - start, Charsets.UTF_8).also { q++ }
            }
            if (v < 2) {
                // Старый вид записи: типа нет, только имя и content_type.
                val id = u16(d, q).toLong(); q += 4 // item_ID + protection_index
                cString()
                Item(id, "mime", if (q < end) cString() else null)
            } else {
                val id = if (v == 2) u16(d, q).toLong().also { q += 2 } else u32(d, q).also { q += 4 }
                q += 2 // protection_index
                val type = String(d, q, 4, Charsets.ISO_8859_1); q += 4
                cString() // item_name
                Item(id, type, if (type == "mime" && q < end) cString() else null)
            }
        }
    }

    private fun locations(d: ByteArray, iloc: Box, idatStart: Long?): Map<Long, List<Extent>> {
        var p = iloc.dataStart.toInt()
        val version = d[p].toInt() and 0xFF
        p += 4
        val offsetSize = (d[p].toInt() shr 4) and 0xF
        val lengthSize = d[p].toInt() and 0xF
        val baseOffsetSize = (d[p + 1].toInt() shr 4) and 0xF
        val indexSize = if (version == 1 || version == 2) d[p + 1].toInt() and 0xF else 0
        p += 2
        val count = if (version < 2) u16(d, p).toLong().also { p += 2 } else u32(d, p).also { p += 4 }
        val result = HashMap<Long, List<Extent>>()
        repeat(count.toInt()) {
            val id = if (version < 2) u16(d, p).toLong().also { p += 2 } else u32(d, p).also { p += 4 }
            var method = 0
            if (version == 1 || version == 2) { method = u16(d, p) and 0xF; p += 2 }
            p += 2 // data_reference_index
            val base = sized(d, p, baseOffsetSize); p += baseOffsetSize
            val extents = u16(d, p); p += 2
            val list = ArrayList<Extent>()
            repeat(extents) {
                p += indexSize
                val off = sized(d, p, offsetSize); p += offsetSize
                val len = sized(d, p, lengthSize); p += lengthSize
                when (method) {
                    0 -> list += Extent(base + off, len)
                    1 -> list += Extent((idatStart ?: throw IOException("No idat")) + base + off, len)
                    else -> {} // ссылки на другие элементы: метаданных так не хранят
                }
            }
            result[id] = list
        }
        return result
    }

    private fun topLevel(f: RandomAccessFile): List<Box> {
        val out = ArrayList<Box>()
        var p = 0L
        val len = f.length()
        val h = ByteArray(16)
        while (p + 8 <= len && out.size < 10_000) {
            f.seek(p); f.readFully(h, 0, 8)
            var size = u32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { f.readFully(h, 8, 8); size = (u32(h, 8) shl 32) or u32(h, 12); header = 16 }
            if (size == 0L) size = len - p
            if (size < header || p + size > len) throw IOException("Bad box $type")
            out += Box(type, p, header, p + size)
            p += size
        }
        return out
    }

    private fun boxes(d: ByteArray, from: Int, to: Int): List<Box> {
        val out = ArrayList<Box>()
        var p = from
        while (p + 8 <= to) {
            var size = u32(d, p)
            val type = String(d, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { size = (u32(d, p + 8) shl 32) or u32(d, p + 12); header = 16 }
            if (size == 0L) size = (to - p).toLong()
            if (size < header || p + size > to) throw IOException("Bad box $type")
            out += Box(type, p.toLong(), header, p + size)
            p += size.toInt()
        }
        return out
    }

    private fun zero(f: RandomAccessFile, from: Long, to: Long) {
        val buf = ByteArray(64 * 1024)
        var p = from
        f.seek(p)
        while (p < to) { val n = minOf(buf.size.toLong(), to - p).toInt(); f.write(buf, 0, n); p += n }
    }

    private fun sized(d: ByteArray, i: Int, size: Int): Long = when (size) {
        0 -> 0
        4 -> u32(d, i)
        8 -> (u32(d, i) shl 32) or u32(d, i + 4)
        else -> throw IOException("Unsupported field size $size")
    }

    private fun u16(d: ByteArray, i: Int) = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
    private fun u32(d: ByteArray, i: Int): Long =
        ((d[i].toLong() and 0xFF) shl 24) or ((d[i + 1].toLong() and 0xFF) shl 16) or
            ((d[i + 2].toLong() and 0xFF) shl 8) or (d[i + 3].toLong() and 0xFF)
}
