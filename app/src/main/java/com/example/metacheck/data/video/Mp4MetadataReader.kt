package com.example.metacheck.data.video

import com.example.metacheck.data.video.IsoBmff.fourcc
import com.example.metacheck.data.video.IsoBmff.s32
import com.example.metacheck.data.video.IsoBmff.u16
import com.example.metacheck.data.video.IsoBmff.u32
import com.example.metacheck.data.video.IsoBmff.u64
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.model.ValueFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Что найдено в контейнере видео. Технические сведения (размер кадра, длительность) — в разделе IMAGE. */
data class VideoMetadata(
    val format: ImageFormat,
    val sections: List<MetaSection>,
    val gps: GpsPoint?,
    val toolFields: List<MetaItem>,
    val freeTextFields: List<MetaItem>,
    val xmpPacket: String?,
    /** Бокс uuid с манифестом C2PA целиком (с заголовком). */
    val c2paBox: ByteArray?,
    val moovBytes: Int,
) {
    /** Есть ли что-то кроме технических параметров потока. */
    val hasPersonalData: Boolean
        get() = gps != null || xmpPacket != null || c2paBox != null ||
            sections.any { it.key != SectionKey.IMAGE && it.items.isNotEmpty() }
}

/** Дорожка moov: идентификатор, тип (vide, soun, meta…) и кодек первой записи stsd. */
internal data class TrackInfo(
    val box: BoxRef,
    val id: Long,
    val handler: String,
    val sampleEntry: String?,
    val width: Int,
    val height: Int,
)

/**
 * Чтение метаданных MP4/MOV без сторонних библиотек: moov/udta (QuickTime ©xyz, ©mak…), moov/meta
 * (ключи mdta от iPhone и Android), 3GPP loci, XMP и C2PA в боксах uuid, время создания в mvhd.
 */
object Mp4MetadataReader {
    const val MAX_MOOV_BYTES = 64 * 1024 * 1024
    private const val MAX_UUID_BYTES = 16 * 1024 * 1024
    private const val MAX_TEXT = 500
    /** Секунды между 1904-01-01 (эпоха QuickTime) и 1970-01-01. */
    const val EPOCH_1904 = 2_082_844_800L

    const val KEY_DURATION = "video:duration"
    const val KEY_CODEC = "video:codec"
    const val KEY_CREATED = "video:created"
    const val KEY_LOCATION = "video:location"
    const val KEY_TITLE = "video:title"

    private val DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)

    private enum class Field(val key: String, val section: SectionKey) {
        MAKE("exif:Make", SectionKey.CAMERA),
        MODEL("exif:Model", SectionKey.CAMERA),
        DATE_TAKEN("exif:DateTimeOriginal", SectionKey.DATES),
        LOCATION(KEY_LOCATION, SectionKey.GPS),
        SOFTWARE("exif:Software", SectionKey.AUTHORSHIP),
        ARTIST("exif:Artist", SectionKey.AUTHORSHIP),
        COPYRIGHT("exif:Copyright", SectionKey.AUTHORSHIP),
        TITLE(KEY_TITLE, SectionKey.AUTHORSHIP),
        DESCRIPTION("exif:ImageDescription", SectionKey.AUTHORSHIP),
    }

    /** Имена тегов QuickTime/iTunes и ключей mdta → поле отчёта. */
    private val names: Map<String, Field> = mapOf(
        "©mak" to Field.MAKE, "com.apple.quicktime.make" to Field.MAKE, "com.android.manufacturer" to Field.MAKE,
        "©mod" to Field.MODEL, "com.apple.quicktime.model" to Field.MODEL, "com.android.model" to Field.MODEL,
        "©day" to Field.DATE_TAKEN, "com.apple.quicktime.creationdate" to Field.DATE_TAKEN,
        "©xyz" to Field.LOCATION, "com.apple.quicktime.location.ISO6709" to Field.LOCATION,
        "©swr" to Field.SOFTWARE, "©too" to Field.SOFTWARE, "©enc" to Field.SOFTWARE,
        "com.apple.quicktime.software" to Field.SOFTWARE, "encoder" to Field.SOFTWARE,
        "©ART" to Field.ARTIST, "©aut" to Field.ARTIST, "auth" to Field.ARTIST,
        "com.apple.quicktime.author" to Field.ARTIST, "com.apple.quicktime.artist" to Field.ARTIST,
        "©cpy" to Field.COPYRIGHT, "cprt" to Field.COPYRIGHT, "com.apple.quicktime.copyright" to Field.COPYRIGHT,
        "©nam" to Field.TITLE, "titl" to Field.TITLE, "com.apple.quicktime.title" to Field.TITLE,
        "©des" to Field.DESCRIPTION, "©cmt" to Field.DESCRIPTION, "©inf" to Field.DESCRIPTION, "dscp" to Field.DESCRIPTION,
        "desc" to Field.DESCRIPTION, "com.apple.quicktime.description" to Field.DESCRIPTION,
        "com.apple.quicktime.comment" to Field.DESCRIPTION,
    )

    fun read(src: ByteSource): VideoMetadata {
        val (top, _) = IsoBmff.list(src, 0, src.size, lenient = true)
        val format = top.firstOrNull { it.type == "ftyp" }?.let { ftyp ->
            if (ftyp.payloadSize >= 4 && fourcc(src.bytes(ftyp.payloadStart, 4), 0) == "qt  ") ImageFormat.MOV else ImageFormat.MP4
        } ?: ImageFormat.MOV
        val moovRef = top.firstOrNull { it.type == "moov" } ?: throw Mp4FormatException("no moov")
        if (moovRef.size > MAX_MOOV_BYTES) throw Mp4FormatException("moov too large")
        val moov = ArrayByteSource(src.bytes(moovRef.start, moovRef.size.toInt()))
        val root = BoxRef("moov", 0, moovRef.header, moovRef.size)

        val tags = Collector()
        var xmp: String? = null
        var c2pa: ByteArray? = null

        for (box in top) when (box.type) {
            "uuid" -> if (box.payloadSize >= 16 && box.size <= MAX_UUID_BYTES) {
                val bytes = src.bytes(box.start, box.size.toInt())
                when {
                    IsoBmff.startsWith(bytes, IsoBmff.XMP_UUID, box.header) && xmp == null ->
                        xmp = String(bytes, box.header + 16, bytes.size - box.header - 16, Charsets.UTF_8)
                    IsoBmff.startsWith(bytes, IsoBmff.C2PA_UUID, box.header) -> c2pa = bytes
                    else -> tags.raw("uuid", bytes.size.toLong())
                }
            } else tags.raw(box.type, box.size)
            "meta", "udta" -> {
                val bytes = ArrayByteSource(src.bytes(box.start, minOf(box.size, MAX_UUID_BYTES.toLong()).toInt()))
                val local = BoxRef(box.type, 0, box.header, bytes.size)
                if (box.type == "meta") readMeta(bytes, local, tags) else readUdta(bytes, local, tags) { xmp = xmp ?: it }
            }
            "ftyp", "moov", "mdat", "free", "skip", "wide" -> Unit
            else -> tags.raw(box.type, box.size)
        }

        var created: Long? = null
        var durationSeconds: Double? = null
        val tracks = ArrayList<TrackInfo>()
        for (child in IsoBmff.children(moov, root)) when (child.type) {
            "mvhd" -> {
                val p = moov.bytes(child.payloadStart, minOf(child.payloadSize, 32L).toInt())
                if (p.size >= 20) {
                    val v1 = p[0].toInt() == 1
                    val creation = if (v1 && p.size >= 32) u64(p, 4) else u32(p, 4)
                    val timescale = if (v1 && p.size >= 32) u32(p, 20) else u32(p, 12)
                    val duration = if (v1 && p.size >= 32) u64(p, 24) else u32(p, 16)
                    if (creation > EPOCH_1904) created = creation - EPOCH_1904
                    if (timescale > 0 && duration in 1 until Long.MAX_VALUE / 2) durationSeconds = duration.toDouble() / timescale
                }
            }
            "trak" -> {
                val track = trackInfo(moov, child)
                if (track != null) tracks += track
                for (sub in IsoBmff.children(moov, child)) when (sub.type) {
                    "udta" -> readUdta(moov, sub, tags) { xmp = xmp ?: it }
                    "meta" -> readMeta(moov, sub, tags)
                }
                if (track != null && track.handler !in setOf("vide", "soun")) {
                    tags.raw("trak ${track.handler}" + (track.sampleEntry?.let { " · $it" } ?: ""), child.size)
                }
            }
            "udta" -> readUdta(moov, child, tags) { xmp = xmp ?: it }
            "meta" -> readMeta(moov, child, tags)
            "uuid" -> {
                val bytes = moov.bytes(child.start, child.size.toInt())
                if (IsoBmff.startsWith(bytes, IsoBmff.XMP_UUID, child.header) && xmp == null) {
                    xmp = String(bytes, child.header + 16, bytes.size - child.header - 16, Charsets.UTF_8)
                } else tags.raw("moov uuid", child.size)
            }
        }

        val image = ArrayList<MetaItem>()
        tracks.firstOrNull { it.handler == "vide" }?.let { video ->
            if (video.width > 0 && video.height > 0) {
                image += MetaItem("ImageWidth", video.width.toString(), "exif:ImageWidth")
                image += MetaItem("ImageLength", video.height.toString(), "exif:ImageLength")
            }
            video.sampleEntry?.trim()?.takeIf { it.isNotEmpty() }?.let { image += MetaItem("Codec", it, KEY_CODEC) }
        }
        durationSeconds?.let { image += MetaItem("Duration", formatDuration(it), KEY_DURATION) }
        created?.let { tags.add(SectionKey.DATES, MetaItem("Created", DATE.format(Instant.ofEpochSecond(it)), KEY_CREATED)) }

        val sections = buildList {
            if (image.isNotEmpty()) add(MetaSection(SectionKey.IMAGE.name, image, SectionKey.IMAGE))
            for (key in listOf(SectionKey.CAMERA, SectionKey.DATES, SectionKey.GPS, SectionKey.AUTHORSHIP, SectionKey.CONTAINER)) {
                val items = tags.items(key)
                if (items.isNotEmpty()) add(MetaSection(key.name, items, key))
            }
        }
        return VideoMetadata(
            format = format,
            sections = sections,
            gps = tags.gps,
            toolFields = tags.tool,
            freeTextFields = tags.freeText,
            xmpPacket = xmp,
            c2paBox = c2pa,
            moovBytes = moovRef.size.toInt(),
        )
    }

    internal fun trackInfo(src: ByteSource, trak: BoxRef): TrackInfo? {
        var id = -1L
        var width = 0
        var height = 0
        var handler: String? = null
        var entry: String? = null
        for (child in IsoBmff.children(src, trak)) when (child.type) {
            "tkhd" -> {
                val p = src.bytes(child.payloadStart, minOf(child.payloadSize, 96L).toInt())
                val v1 = p.isNotEmpty() && p[0].toInt() == 1
                val idAt = if (v1) 20 else 12
                if (p.size >= idAt + 4) id = u32(p, idAt)
                // Ширина и высота — последние 8 байт tkhd, числа 16.16.
                if (p.size >= 8 && child.payloadSize <= 96) {
                    width = (u32(p, p.size - 8) shr 16).toInt()
                    height = (u32(p, p.size - 4) shr 16).toInt()
                }
            }
            "mdia" -> for (m in IsoBmff.children(src, child)) when (m.type) {
                "hdlr" -> if (m.payloadSize >= 12) handler = fourcc(src.bytes(m.payloadStart + 8, 4), 0)
                "minf" -> IsoBmff.children(src, m).firstOrNull { it.type == "stbl" }?.let { stbl ->
                    IsoBmff.children(src, stbl).firstOrNull { it.type == "stsd" }?.let { stsd ->
                        if (stsd.payloadSize >= 16) entry = fourcc(src.bytes(stsd.payloadStart + 12, 4), 0)
                    }
                }
            }
        }
        return handler?.let { TrackInfo(trak, id, it, entry, width, height) }
    }

    /** udta: теги QuickTime (©xxx), 3GPP (loci, auth…), вложенный meta/ilst, XMP_ от Adobe. */
    private fun readUdta(src: ByteSource, udta: BoxRef, tags: Collector, onXmp: (String) -> Unit) {
        val children = try { IsoBmff.children(src, udta) } catch (_: Mp4FormatException) { return }
        for (child in children) {
            val size = child.payloadSize
            if (size > MAX_UUID_BYTES) { tags.raw("udta ${child.type}", child.size); continue }
            when {
                child.type == "meta" -> readMeta(src, child, tags)
                child.type == "XMP_" -> onXmp(String(src.bytes(child.payloadStart, size.toInt()), Charsets.UTF_8))
                child.type == "loci" -> readLoci(src.bytes(child.payloadStart, size.toInt()), tags)
                child.type.startsWith("©") || child.type in names -> {
                    val p = src.bytes(child.payloadStart, size.toInt())
                    val text = quickTimeText(p, src, child) ?: threeGppText(p)
                    if (text != null) tags.named(child.type, text) else tags.raw("udta ${child.type}", child.size)
                }
                else -> tags.raw("udta ${child.type}", child.size)
            }
        }
    }

    /** Текст тега QuickTime: [длина u16][язык u16][текст], либо вложенный бокс data (стиль iTunes). */
    private fun quickTimeText(p: ByteArray, src: ByteSource, box: BoxRef): String? {
        if (p.size >= 16 && fourcc(p, 4) == "data") {
            return IsoBmff.list(src, box.payloadStart, box.end, lenient = true).first
                .firstOrNull { it.type == "data" }?.let { dataValue(src, it) }
        }
        if (p.size < 4) return null
        val len = u16(p, 0)
        if (len == 0 || 4 + len > p.size) return null
        return clean(String(p, 4, len, Charsets.UTF_8))
    }

    /** 3GPP (titl, auth, dscp, cprt): FullBox, язык u16, строка UTF-8 или UTF-16 с BOM. */
    private fun threeGppText(p: ByteArray): String? {
        if (p.size < 7) return null
        val body = p.copyOfRange(6, p.size)
        val text = if (body.size >= 2 && body[0] == 0xFE.toByte() && body[1] == 0xFF.toByte()) {
            String(body, 2, body.size - 2, Charsets.UTF_16BE)
        } else String(body, Charsets.UTF_8)
        return clean(text.substringBefore('\u0000'))
    }

    /** loci: FullBox, язык, имя места (строка с нулём), роль, затем долгота, широта, высота в 16.16. */
    private fun readLoci(p: ByteArray, tags: Collector) {
        if (p.size < 7) return
        var i = 6
        val name = if (i + 1 < p.size && p[i] == 0xFE.toByte() && p[i + 1] == 0xFF.toByte()) {
            var e = i + 2
            while (e + 1 < p.size && !(p[e].toInt() == 0 && p[e + 1].toInt() == 0)) e += 2
            String(p, i + 2, e - i - 2, Charsets.UTF_16BE).also { i = e + 2 }
        } else {
            var e = i
            while (e < p.size && p[e].toInt() != 0) e++
            String(p, i, e - i, Charsets.UTF_8).also { i = e + 1 }
        }
        i += 1 // роль
        clean(name)?.let { tags.raw("loci", it) }
        if (i + 12 > p.size) return
        val lon = s32(p, i) / 65536.0
        val lat = s32(p, i + 4) / 65536.0
        val alt = s32(p, i + 8) / 65536.0
        tags.location(String.format(Locale.ROOT, "%+.6f%+.6f%+.1f/", lat, lon, alt), lat, lon, alt)
    }

    /** meta: QuickTime (hdlr mdta + keys + ilst) или iTunes (hdlr mdir + ilst с ©xxx). */
    private fun readMeta(src: ByteSource, meta: BoxRef, tags: Collector) {
        // В MP4 meta — FullBox (4 байта версии и флагов), в QuickTime — обычный контейнер.
        val head = src.bytes(meta.payloadStart, minOf(meta.payloadSize, 12L).toInt())
        val skip = if (head.size >= 8 && fourcc(head, 4) == "hdlr") 0 else 4
        val children = try { IsoBmff.list(src, meta.payloadStart + skip, meta.end).first } catch (_: Mp4FormatException) { return }
        val keys = ArrayList<String>()
        children.firstOrNull { it.type == "keys" }?.let { box ->
            val p = src.bytes(box.payloadStart, minOf(box.payloadSize, MAX_UUID_BYTES.toLong()).toInt())
            if (p.size >= 8) {
                val count = u32(p, 4)
                var i = 8
                var n = 0L
                while (n < count && i + 8 <= p.size) {
                    val size = u32(p, i).toInt()
                    if (size < 8 || i + size > p.size) break
                    keys += String(p, i + 8, size - 8, Charsets.UTF_8)
                    i += size; n++
                }
            }
        }
        for (box in children) when (box.type) {
            "ilst" -> for (item in IsoBmff.list(src, box.payloadStart, box.end, lenient = true).first) {
                // В ilst QuickTime тип элемента — номер ключа (с 1), у iTunes — четыре буквы.
                val index = item.type.toByteArray(Charsets.ISO_8859_1).let { u32(it, 0) }
                val name = if (keys.isNotEmpty() && index in 1..keys.size.toLong()) keys[(index - 1).toInt()] else item.type
                val data = IsoBmff.list(src, item.payloadStart, item.end, lenient = true).first.firstOrNull { it.type == "data" }
                val value = data?.let { dataValue(src, it) }
                if (value != null) tags.named(name, value) else tags.raw(name, item.size)
            }
            "hdlr", "keys", "free" -> Unit
            "XMP_" -> Unit
            else -> tags.raw("meta ${box.type}", box.size)
        }
    }

    /** Значение бокса data: тип (1 UTF-8, 2 UTF-16, 21/22 целое, 23/24 дробное), локаль, данные. */
    private fun dataValue(src: ByteSource, box: BoxRef): String? {
        if (box.payloadSize < 8 || box.payloadSize > MAX_UUID_BYTES) return null
        val p = src.bytes(box.payloadStart, box.payloadSize.toInt())
        val type = (u32(p, 0) and 0xFFFFFF).toInt()
        val len = p.size - 8
        return when (type) {
            1, 4 -> clean(String(p, 8, len, Charsets.UTF_8))
            2, 5 -> clean(String(p, 8, len, Charsets.UTF_16BE))
            21, 22 -> when (len) {
                1 -> (if (type == 21) p[8].toInt() else p[8].toInt() and 0xFF).toString()
                2 -> u16(p, 8).let { if (type == 21) it.toShort().toInt() else it }.toString()
                4 -> (if (type == 21) s32(p, 8).toLong() else u32(p, 8)).toString()
                8 -> u64(p, 8).toString()
                else -> null
            }
            23 -> if (len == 4) java.lang.Float.intBitsToFloat(s32(p, 8)).toString() else null
            24 -> if (len == 8) java.lang.Double.longBitsToDouble(u64(p, 8)).toString() else null
            0 -> clean(String(p, 8, len, Charsets.UTF_8))?.takeIf { s -> s.all { !it.isISOControl() } }
            else -> null
        }
    }

    private fun clean(raw: String): String? =
        raw.filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim().take(MAX_TEXT).takeIf { it.isNotEmpty() }

    fun formatDuration(seconds: Double): String {
        val total = seconds.toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /**
     * ISO 6709 из телефонов: «+41.8902+012.4923+050.000/». Поддерживаются и записи в градусах-минутах
     * («+4153.41+01229.54»), их пишут некоторые камеры.
     */
    fun parseIso6709(text: String): GpsPoint? {
        val m = Regex("^([+-][0-9]+(?:\\.[0-9]+)?)([+-][0-9]+(?:\\.[0-9]+)?)([+-][0-9]+(?:\\.[0-9]+)?)?").find(text.trim())
            ?: return null
        val lat = degrees(m.groupValues[1], 2) ?: return null
        val lon = degrees(m.groupValues[2], 3) ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat == 0.0 && lon == 0.0)) return null
        val alt = m.groups[3]?.value?.toDoubleOrNull()?.takeIf { it.isFinite() }
        return GpsPoint(lat, lon, alt)
    }

    private fun degrees(value: String, degreeDigits: Int): Double? {
        val sign = if (value[0] == '-') -1.0 else 1.0
        val body = value.substring(1)
        val intLen = body.indexOf('.').let { if (it < 0) body.length else it }
        val number = body.toDoubleOrNull() ?: return null
        val result = when (intLen) {
            degreeDigits -> number
            degreeDigits + 2 -> {
                val deg = body.substring(0, degreeDigits).toDouble()
                deg + (number - deg * 100) / 60.0
            }
            degreeDigits + 4 -> {
                val deg = body.substring(0, degreeDigits).toDouble()
                val min = body.substring(degreeDigits, degreeDigits + 2).toDouble()
                deg + min / 60.0 + (number - deg * 10_000 - min * 100) / 3600.0
            }
            else -> return null
        }
        return (sign * result).takeIf { it.isFinite() }
    }

    /** Собирает теги по разделам без повторов. */
    private class Collector {
        private val bySection = LinkedHashMap<SectionKey, LinkedHashMap<String, MetaItem>>()
        val tool = ArrayList<MetaItem>()
        val freeText = ArrayList<MetaItem>()
        var gps: GpsPoint? = null

        fun add(section: SectionKey, item: MetaItem) {
            bySection.getOrPut(section) { LinkedHashMap() }.putIfAbsent("${item.key ?: item.label}=${item.value}", item)
        }

        fun items(section: SectionKey): List<MetaItem> = bySection[section]?.values?.toList().orEmpty()

        fun named(name: String, value: String) {
            val field = names[name]
            if (field == null) { raw(name, value); return }
            if (field == Field.LOCATION) {
                val point = parseIso6709(value)
                location(value, point?.latitude, point?.longitude, point?.altitudeMeters)
                return
            }
            add(field.section, MetaItem(name, value, field.key))
            val label = "MP4 $name"
            if (field == Field.SOFTWARE) tool += MetaItem(label, value)
            if (field == Field.DESCRIPTION || field == Field.TITLE || field == Field.ARTIST) freeText += MetaItem(label, value)
        }

        fun location(text: String, lat: Double?, lon: Double?, alt: Double?) {
            add(SectionKey.GPS, MetaItem("Location", text.take(MAX_TEXT), KEY_LOCATION))
            if (gps == null && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0 &&
                !(lat == 0.0 && lon == 0.0)) {
                gps = GpsPoint(lat, lon, alt)
            }
        }

        fun raw(label: String, value: String) = add(SectionKey.CONTAINER, MetaItem(label.take(120), value))

        fun raw(label: String, bytes: Long) =
            add(SectionKey.CONTAINER, MetaItem(label.take(120), bytes.toString(), format = ValueFormat.BYTES))
    }
}
