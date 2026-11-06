package com.example.metacheck.data.video

import com.example.metacheck.data.video.IsoBmff.putU32
import com.example.metacheck.data.video.IsoBmff.putU64
import com.example.metacheck.data.video.IsoBmff.u32
import com.example.metacheck.data.video.IsoBmff.u64
import java.io.OutputStream

/**
 * Очистка MP4/MOV без пережатия: видео и звук копируются байт в байт, меняется только описание файла.
 *
 * Что удаляется:
 * - теги udta и meta на всех уровнях (место съёмки ©xyz, модель телефона, программа, автор, ключи iPhone);
 * - боксы uuid (XMP, C2PA) и все прочие боксы верхнего уровня, кроме ftyp, moov и mdat;
 * - дорожки, кроме видео и звука (таймкод с временем съёмки, дорожки метаданных с GPS), а их данные в mdat
 *   заменяются нулями;
 * - время создания и изменения в mvhd, tkhd и mdhd (обнуляется).
 *
 * Смещения фрагментов (stco/co64) пересчитываются под новое расположение mdat.
 * Фрагментированные и зашифрованные файлы не поддерживаются: [Mp4FormatException].
 */
object Mp4Cleaner {
    private val KEEP_TOP = setOf("ftyp", "moov", "mdat")
    val KEEP_HANDLERS = setOf("vide", "soun", "auxv")
    private val MOOV_KEEP = setOf("mvhd", "trak", "iods")
    private val TRAK_KEEP = setOf("tkhd", "edts", "tref", "mdia", "tapt", "load", "clip", "matt")
    private val MDIA_KEEP = setOf("mdhd", "hdlr", "minf", "elng")
    private val ALWAYS_DROP = setOf("udta", "meta", "uuid", "free", "skip", "wide", "XMP_")
    private const val COPY_BUFFER = 1 shl 20

    data class Summary(val outputBytes: Long, val removedTracks: Int, val zeroedBytes: Long)

    /** Подготовленный план записи: проверен целиком до того, как записан первый байт. */
    class Plan internal constructor(
        internal val src: ByteSource,
        internal val top: List<BoxRef>,
        internal val moovRef: BoxRef,
        internal val moov: Node,
        internal val zero: List<LongRange>,
        val removedTracks: Int,
    ) {
        val outputBytes: Long = top.sumOf { if (it === moovRef) moov.size else it.size }
    }

    fun plan(src: ByteSource): Plan {
        // Нераспознанный хвост файла (трейлеры камер) не копируется вовсе.
        val (all, _) = IsoBmff.list(src, 0, src.size, lenient = true)
        if (all.any { it.type == "moof" || it.type == "mfra" || it.type == "sidx" || it.type == "styp" }) {
            throw Mp4FormatException("fragmented")
        }
        val moovs = all.filter { it.type == "moov" }
        if (moovs.size != 1) throw Mp4FormatException("moov count ${moovs.size}")
        val moovRef = moovs[0]
        if (moovRef.size > Mp4MetadataReader.MAX_MOOV_BYTES) throw Mp4FormatException("moov too large")
        val top = all.filter { it.type in KEEP_TOP }
        val mdats = top.filter { it.type == "mdat" }
        if (mdats.isEmpty()) throw Mp4FormatException("no mdat")

        val moovBytes = src.bytes(moovRef.start, moovRef.size.toInt())
        val m = ArrayByteSource(moovBytes)
        val root = BoxRef("moov", 0, moovRef.header, moovRef.size)
        val moovChildren = IsoBmff.children(m, root)
        if (moovChildren.any { it.type == "mvex" || it.type == "cmov" }) throw Mp4FormatException("fragmented or compressed moov")

        val tracks = moovChildren.filter { it.type == "trak" }.map { trak ->
            Mp4MetadataReader.trackInfo(m, trak) ?: throw Mp4FormatException("track without handler")
        }
        val kept = tracks.filter { it.handler in KEEP_HANDLERS }
        if (kept.isEmpty()) throw Mp4FormatException("no audio or video tracks")
        val keptIds = kept.map { it.id }.toSet()

        // Данные удаляемых дорожек (GPS, таймкод) затираются нулями. Начало фрагмента оставляемой дорожки
        // внутри затираемого диапазона значит, что таблицы противоречат друг другу: не рискуем.
        val keptStarts = kept.flatMap { chunkOffsets(m, it.box).toList() }.sorted()
        val zero = merge(tracks.filter { it.handler !in KEEP_HANDLERS }.flatMap { chunkRanges(m, it.box) })
        for (range in zero) {
            val i = keptStarts.binarySearch(range.first).let { if (it < 0) -it - 1 else it }
            if (i < keptStarts.size && keptStarts[i] <= range.last) throw Mp4FormatException("overlapping tracks")
        }

        val children = moovChildren.mapNotNull { box ->
            when (box.type) {
                "mvhd" -> Raw(zeroTimes(m.bytes(box.start, box.size.toInt()), box.header))
                "iods" -> Raw(m.bytes(box.start, box.size.toInt()))
                "trak" -> tracks.first { it.box == box }.takeIf { it.handler in KEEP_HANDLERS }?.let { cleanTrak(m, box, keptIds) }
                else -> null
            }
        }
        val moov = Container("moov", children)
        if (moov.size > 0xFFFFFFFFL) throw Mp4FormatException("moov too large")
        return Plan(src, top, moovRef, moov, zero, tracks.size - kept.size).also { resolveOffsets(it) }
    }

    /**
     * Очищенный файл как источник байтов, без записи на диск: его можно проверить [isClean] до записи,
     * а [write] пишет ровно эти байты.
     */
    fun output(plan: Plan): ByteSource = OutputView(plan)

    /** Записывает очищенный файл. [checkCancelled] вызывается на каждом мегабайте. */
    fun write(plan: Plan, out: OutputStream, checkCancelled: () -> Unit = {}): Summary {
        val view = OutputView(plan)
        val buffer = ByteArray(COPY_BUFFER)
        var pos = 0L
        while (pos < view.size) {
            checkCancelled()
            val n = minOf(buffer.size.toLong(), view.size - pos).toInt()
            view.readFully(pos, buffer, 0, n)
            out.write(buffer, 0, n)
            pos += n
        }
        out.flush()
        return Summary(pos, plan.removedTracks, plan.zero.sumOf { it.last - it.first + 1 })
    }

    /** Отрезки выходного файла: новый moov или байты исходника (в mdat — с обнулёнными диапазонами). */
    private class OutputView(private val plan: Plan) : ByteSource {
        private val moovBytes = plan.moov.serialize()
        private val starts: LongArray
        override val size: Long

        init {
            var pos = 0L
            starts = LongArray(plan.top.size) { i ->
                val box = plan.top[i]
                pos.also { pos += if (box === plan.moovRef) moovBytes.size.toLong() else box.size }
            }
            size = pos
        }

        override fun readFully(position: Long, dst: ByteArray, offset: Int, length: Int) {
            if (position < 0 || length < 0 || position + length > size) throw java.io.EOFException()
            var pos = position
            var off = offset
            var left = length
            var i = starts.indexOfLast { it <= pos }
            while (left > 0) {
                val box = plan.top[i]
                val boxSize = if (box === plan.moovRef) moovBytes.size.toLong() else box.size
                val inBox = pos - starts[i]
                val n = minOf(left.toLong(), boxSize - inBox).toInt()
                if (box === plan.moovRef) {
                    System.arraycopy(moovBytes, inBox.toInt(), dst, off, n)
                } else {
                    plan.src.readFully(box.start + inBox, dst, off, n)
                    if (box.type == "mdat") applyZero(dst, off, box.start + inBox, n, plan.zero, box.payloadStart)
                }
                pos += n; off += n; left -= n; i++
            }
        }
    }

    fun clean(src: ByteSource, out: OutputStream): Summary = write(plan(src), out)

    /**
     * Независимая проверка готового файла: только ftyp/moov/mdat, в moov нет тегов и лишних дорожек,
     * время создания обнулено, а чтение метаданных ничего личного не находит.
     */
    fun isClean(src: ByteSource): Boolean = try {
        val (top, end) = IsoBmff.list(src, 0, src.size, lenient = false)
        val moov = top.singleOrNull { it.type == "moov" }
        if (end != src.size || top.any { it.type !in KEEP_TOP } || moov == null || moov.size > Mp4MetadataReader.MAX_MOOV_BYTES) {
            false
        } else {
            val m = ArrayByteSource(src.bytes(moov.start, moov.size.toInt()))
            val root = BoxRef("moov", 0, moov.header, moov.size)
            structureClean(m, root) && !Mp4MetadataReader.read(src).hasPersonalData
        }
    } catch (_: Exception) {
        false
    }

    private fun structureClean(m: ByteSource, root: BoxRef): Boolean {
        for (child in IsoBmff.children(m, root)) {
            if (child.type !in MOOV_KEEP) return false
            if (child.type == "mvhd" && !timesZero(m, child)) return false
            if (child.type != "trak") continue
            val info = Mp4MetadataReader.trackInfo(m, child) ?: return false
            if (info.handler !in KEEP_HANDLERS) return false
            for (t in IsoBmff.children(m, child)) {
                if (t.type !in TRAK_KEEP) return false
                if (t.type == "tkhd" && !timesZero(m, t)) return false
                if (t.type != "mdia") continue
                for (md in IsoBmff.children(m, t)) {
                    if (md.type !in MDIA_KEEP) return false
                    if (md.type == "mdhd" && !timesZero(m, md)) return false
                    if (md.type != "minf") continue
                    for (mi in IsoBmff.children(m, md)) {
                        if (mi.type in ALWAYS_DROP) return false
                        if (mi.type == "stbl" && IsoBmff.children(m, mi).any { it.type in ALWAYS_DROP }) return false
                    }
                }
            }
        }
        return true
    }

    // --- Дорожки ---

    private fun cleanTrak(m: ByteSource, trak: BoxRef, keptIds: Set<Long>): Node = Container("trak",
        IsoBmff.children(m, trak).mapNotNull { box ->
            when (box.type) {
                "tkhd" -> Raw(zeroTimes(m.bytes(box.start, box.size.toInt()), box.header))
                "mdia" -> cleanMdia(m, box)
                "tref" -> cleanTref(m, box, keptIds)
                in TRAK_KEEP -> Raw(m.bytes(box.start, box.size.toInt()))
                else -> null
            }
        })

    private fun cleanMdia(m: ByteSource, mdia: BoxRef): Node = Container("mdia",
        IsoBmff.children(m, mdia).mapNotNull { box ->
            when (box.type) {
                "mdhd" -> Raw(zeroTimes(m.bytes(box.start, box.size.toInt()), box.header))
                "minf" -> Container("minf", IsoBmff.children(m, box).mapNotNull { mi ->
                    when {
                        mi.type in ALWAYS_DROP -> null
                        mi.type == "stbl" -> cleanStbl(m, mi)
                        else -> Raw(m.bytes(mi.start, mi.size.toInt()))
                    }
                })
                in MDIA_KEEP -> Raw(m.bytes(box.start, box.size.toInt()))
                else -> null
            }
        })

    private fun cleanStbl(m: ByteSource, stbl: BoxRef): Node = Container("stbl",
        IsoBmff.children(m, stbl).mapNotNull { box ->
            when (box.type) {
                in ALWAYS_DROP -> null
                // Вспомогательные данные шифрования ссылаются в mdat абсолютными смещениями: не пересчитываем.
                "saio", "saiz" -> throw Mp4FormatException("sample auxiliary info")
                "stco", "co64" -> {
                    val p = m.bytes(box.payloadStart, box.payloadSize.toInt())
                    ChunkOffsets(box.type, IsoBmff.u32(p, 0), readOffsets(box.type, p))
                }
                else -> Raw(m.bytes(box.start, box.size.toInt()))
            }
        })

    /** tref: ссылки только на оставшиеся дорожки; пустой tref удаляется. */
    private fun cleanTref(m: ByteSource, tref: BoxRef, keptIds: Set<Long>): Node? {
        val refs = IsoBmff.children(m, tref).mapNotNull { ref ->
            val p = m.bytes(ref.payloadStart, ref.payloadSize.toInt())
            val ids = (0 until p.size / 4).map { u32(p, it * 4) }.filter { it in keptIds }
            if (ids.isEmpty()) null else Raw(ByteArray(8 + ids.size * 4).also { b ->
                putU32(b, 0, b.size.toLong())
                System.arraycopy(ref.type.toByteArray(Charsets.ISO_8859_1), 0, b, 4, 4)
                ids.forEachIndexed { i, id -> putU32(b, 8 + i * 4, id) }
            })
        }
        return if (refs.isEmpty()) null else Container("tref", refs)
    }

    /** Время создания и изменения: у версии 0 — по 4 байта, у версии 1 — по 8. */
    private fun zeroTimes(box: ByteArray, header: Int): ByteArray {
        if (box.size < header + 4) throw Mp4FormatException("short header box")
        val v1 = box[header].toInt() == 1
        val need = header + 4 + if (v1) 16 else 8
        if (box.size < need) throw Mp4FormatException("short header box")
        if (v1) { putU64(box, header + 4, 0); putU64(box, header + 12, 0) } else { putU32(box, header + 4, 0); putU32(box, header + 8, 0) }
        return box
    }

    private fun timesZero(m: ByteSource, box: BoxRef): Boolean {
        val p = m.bytes(box.payloadStart, minOf(box.payloadSize, 20L).toInt())
        if (p.isEmpty()) return false
        return if (p[0].toInt() == 1) p.size >= 20 && u64(p, 4) == 0L && u64(p, 12) == 0L
        else p.size >= 12 && u32(p, 4) == 0L && u32(p, 8) == 0L
    }

    // --- Таблицы фрагментов ---

    private fun stbl(m: ByteSource, trak: BoxRef): BoxRef? =
        IsoBmff.children(m, trak).firstOrNull { it.type == "mdia" }
            ?.let { IsoBmff.children(m, it).firstOrNull { b -> b.type == "minf" } }
            ?.let { IsoBmff.children(m, it).firstOrNull { b -> b.type == "stbl" } }

    private fun readOffsets(type: String, p: ByteArray): LongArray {
        if (p.size < 8) throw Mp4FormatException("short $type")
        val count = u32(p, 4)
        val width = if (type == "co64") 8 else 4
        if (count > (p.size - 8) / width) throw Mp4FormatException("bad $type count")
        return LongArray(count.toInt()) { if (width == 8) u64(p, 8 + it * 8) else u32(p, 8 + it * 4) }
    }

    private fun chunkOffsets(m: ByteSource, trak: BoxRef): LongArray {
        val table = stbl(m, trak) ?: return LongArray(0)
        val box = IsoBmff.children(m, table).firstOrNull { it.type == "stco" || it.type == "co64" } ?: return LongArray(0)
        return readOffsets(box.type, m.bytes(box.payloadStart, box.payloadSize.toInt()))
    }

    /** Байтовые диапазоны фрагментов дорожки по stco/co64, stsc и stsz. */
    private fun chunkRanges(m: ByteSource, trak: BoxRef): List<LongRange> {
        val table = stbl(m, trak) ?: return emptyList()
        val boxes = IsoBmff.children(m, table).associateBy { it.type }
        val offsets = chunkOffsets(m, trak)
        if (offsets.isEmpty()) return emptyList()
        if ("stz2" in boxes) throw Mp4FormatException("stz2")
        val stsz = boxes["stsz"] ?: throw Mp4FormatException("no stsz")
        val stsc = boxes["stsc"] ?: throw Mp4FormatException("no stsc")
        val sz = m.bytes(stsz.payloadStart, stsz.payloadSize.toInt())
        val sc = m.bytes(stsc.payloadStart, stsc.payloadSize.toInt())
        if (sz.size < 12 || sc.size < 8) throw Mp4FormatException("short tables")
        val fixed = u32(sz, 4)
        val samples = u32(sz, 8)
        if (fixed == 0L && samples > (sz.size - 12) / 4) throw Mp4FormatException("bad stsz")
        val entries = u32(sc, 4)
        if (entries > (sc.size - 8) / 12) throw Mp4FormatException("bad stsc")
        val ranges = ArrayList<LongRange>(offsets.size)
        var sample = 0L
        for (e in 0 until entries.toInt()) {
            val first = u32(sc, 8 + e * 12)
            val perChunk = u32(sc, 12 + e * 12)
            val next = if (e + 1 < entries) u32(sc, 8 + (e + 1) * 12) else offsets.size + 1L
            if (first < 1 || next <= first) throw Mp4FormatException("bad stsc order")
            var chunk = first
            while (chunk < next && chunk <= offsets.size) {
                var length = 0L
                repeat(perChunk.toInt()) {
                    if (sample >= samples) throw Mp4FormatException("sample count")
                    length += if (fixed != 0L) fixed else u32(sz, 12 + (sample * 4).toInt())
                    sample++
                }
                val start = offsets[(chunk - 1).toInt()]
                if (length > 0) ranges += start until start + length
                chunk++
            }
        }
        return ranges
    }

    private fun merge(ranges: List<LongRange>): List<LongRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val out = ArrayList<LongRange>()
        var cur = sorted[0]
        for (r in sorted.drop(1)) {
            cur = if (r.first <= cur.last + 1) cur.first..maxOf(cur.last, r.last) else { out += cur; r }
        }
        out += cur
        return out
    }

    /**
     * Обнуляет байты из [zero] в куске буфера: [off] — начало куска в буфере, [pos] — его позиция в исходнике.
     * Заголовок mdat (до [payloadStart]) не трогается.
     */
    private fun applyZero(buffer: ByteArray, off: Int, pos: Long, n: Int, zero: List<LongRange>, payloadStart: Long) {
        val end = pos + n - 1
        for (r in zero) {
            if (r.last < pos) continue
            if (r.first > end) break
            val from = maxOf(r.first, pos, payloadStart)
            val to = minOf(r.last, end)
            if (from > to) continue
            java.util.Arrays.fill(buffer, off + (from - pos).toInt(), off + (to - pos + 1).toInt(), 0)
        }
    }

    /** Новые смещения фрагментов: каждое должно лежать внутри одного из сохраняемых mdat. */
    private fun resolveOffsets(plan: Plan) {
        var pos = 0L
        val newStart = HashMap<BoxRef, Long>()
        for (box in plan.top) {
            newStart[box] = pos
            pos += if (box === plan.moovRef) plan.moov.size else box.size
        }
        val mdats = plan.top.filter { it.type == "mdat" }
        plan.moov.visit { node ->
            if (node !is ChunkOffsets) return@visit
            for (i in node.offsets.indices) {
                val old = node.offsets[i]
                // Пустой фрагмент может указывать ровно на конец mdat.
                val mdat = mdats.firstOrNull { old >= it.payloadStart && old <= it.end }
                    ?: throw Mp4FormatException("chunk outside mdat")
                val moved = newStart.getValue(mdat) + (old - mdat.start)
                if (node.type == "stco" && moved > 0xFFFFFFFFL) throw Mp4FormatException("offset overflow")
                node.offsets[i] = moved
            }
        }
    }

    // --- Новый moov ---

    internal sealed class Node {
        abstract val size: Long
        abstract fun writeTo(out: ByteArray, at: Int): Int
        open fun visit(action: (Node) -> Unit) = action(this)
        fun serialize(): ByteArray = ByteArray(size.toInt()).also { writeTo(it, 0) }
    }

    private class Raw(val bytes: ByteArray) : Node() {
        override val size: Long get() = bytes.size.toLong()
        override fun writeTo(out: ByteArray, at: Int): Int { System.arraycopy(bytes, 0, out, at, bytes.size); return at + bytes.size }
    }

    private class Container(val type: String, val children: List<Node>) : Node() {
        override val size: Long = 8 + children.sumOf { it.size }
        override fun writeTo(out: ByteArray, at: Int): Int {
            putU32(out, at, size)
            System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, out, at + 4, 4)
            var p = at + 8
            for (c in children) p = c.writeTo(out, p)
            return p
        }
        override fun visit(action: (Node) -> Unit) { action(this); children.forEach { it.visit(action) } }
    }

    private class ChunkOffsets(val type: String, val versionFlags: Long, val offsets: LongArray) : Node() {
        private val width = if (type == "co64") 8 else 4
        override val size: Long = 16L + offsets.size * width
        override fun writeTo(out: ByteArray, at: Int): Int {
            putU32(out, at, size)
            System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, out, at + 4, 4)
            putU32(out, at + 8, versionFlags)
            putU32(out, at + 12, offsets.size.toLong())
            var p = at + 16
            for (o in offsets) { if (width == 8) putU64(out, p, o) else putU32(out, p, o); p += width }
            return p
        }
    }
}
