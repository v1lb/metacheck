package com.example.metacheck.data.metadata

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Потоковый разбор JPEG по маркерам, включая энтропийные данные после SOS.
 *
 * Почему недостаточно скопировать «всё после SOS» (так было раньше):
 *  - после EOI основного кадра телефоны дописывают Motion Photo (MP4 с геотегом, Pixel/Samsung),
 *    вторичные MPF-кадры и gain map Ultra HDR (со своим EXIF), трейлеры Samsung;
 *  - progressive JPEG содержит несколько сканов: между ними идут DHT/SOS, их тоже нужно разбирать;
 *  - миниатюра EXIF (IFD1) у обрезанного/отредактированного фото показывает исходный кадр.
 *
 * Санитайзер оставляет только основной кадр до первого EOI и отбрасывает всё, что после него.
 */
internal object JpegSegments {

    /** Что найдено в файле. Используется и для проверки результата, и в тестах. */
    data class Findings(
        val exif: Boolean = false,
        val exifThumbnail: Boolean = false,
        val xmp: Boolean = false,
        val iptc: Boolean = false,
        val comment: Boolean = false,
        val mpf: Boolean = false,
        /** Есть байты после EOI основного кадра (Motion Photo, MPF-кадры, трейлеры). */
        val trailer: Boolean = false,
    ) {
        /** Скрытые места, где могут оставаться координаты или исходное изображение. */
        val hasHiddenPayload: Boolean get() = xmp || iptc || comment || mpf || trailer || exifThumbnail
    }

    private val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
    private val xmpStandard = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
    private val xmpExtended = "http://ns.adobe.com/xmp/extension/\u0000".toByteArray(Charsets.US_ASCII)
    private val photoshop = "Photoshop 3.0\u0000".toByteArray(Charsets.US_ASCII)
    private val mpfHeader = "MPF\u0000".toByteArray(Charsets.US_ASCII)

    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val TEM = 0x01
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2
    private const val APP13 = 0xED
    private const val COM = 0xFE

    /**
     * Копирует JPEG, удаляя XMP, IPTC, COM, MPF и всё после EOI основного кадра.
     * EXIF (APP1) сохраняется. При [dropExifThumbnail] миниатюра IFD1 удаляется из EXIF.
     */
    fun removeContradictorySegments(source: File, destination: File, dropExifThumbnail: Boolean = false) {
        BufferedInputStream(FileInputStream(source), BUF).use { input ->
            BufferedOutputStream(FileOutputStream(destination), BUF).use { output ->
                walk(input, output, sanitize = true, dropExifThumbnail = dropExifThumbnail)
            }
        }
    }

    /** Только анализ, файл не меняется. Бросает IOException для повреждённого JPEG. */
    fun inspect(source: File): Findings =
        BufferedInputStream(FileInputStream(source), BUF).use { input ->
            walk(input, null, sanitize = false, dropExifThumbnail = false)
        }

    /** Свежий результат Bitmap.compress: никаких метаданных и ничего после EOI. */
    fun isStrippedJpeg(source: File): Boolean = try {
        val f = inspect(source)
        !f.exif && !f.hasHiddenPayload
    } catch (_: IOException) {
        false
    }

    private fun walk(input: InputStream, out: OutputStream?, sanitize: Boolean, dropExifThumbnail: Boolean): Findings {
        if (input.read() != 0xFF || input.read() != SOI) throw IOException("Invalid JPEG SOI")
        out?.write(0xFF); out?.write(SOI)

        var findings = Findings()
        var inScan = false
        while (true) {
            val marker = if (inScan) copyEntropyUntilMarker(input, out) else readMarker(input)
            inScan = false
            when {
                marker == EOI -> {
                    out?.write(0xFF); out?.write(EOI)
                    // Всё, что после EOI основного кадра, не записывается.
                    val trailer = input.read() >= 0
                    return findings.copy(trailer = trailer)
                }
                marker == TEM || marker in 0xD0..0xD7 -> {
                    // Маркеры без длины. RST вне скана формально некорректен, но безвреден.
                    out?.write(0xFF); out?.write(marker)
                }
                marker == SOI -> throw IOException("Nested SOI before EOI")
                else -> {
                    val high = input.read(); val low = input.read()
                    if (high < 0 || low < 0) throw IOException("Truncated segment length")
                    val length = (high shl 8) or low
                    if (length < 2) throw IOException("Invalid segment length")
                    var data = ByteArray(length - 2)
                    readCompletely(input, data)

                    val isExif = marker == APP1 && data.startsWithBytes(exifHeader)
                    val isXmp = marker == APP1 && (data.startsWithBytes(xmpStandard) || data.startsWithBytes(xmpExtended))
                    val isIptc = marker == APP13 && data.startsWithBytes(photoshop)
                    val isComment = marker == COM
                    val isMpf = marker == APP2 && data.startsWithBytes(mpfHeader)

                    if (isExif) {
                        val hasThumb = ExifThumbnail.present(data)
                        findings = findings.copy(exif = true, exifThumbnail = findings.exifThumbnail || hasThumb)
                        if (sanitize && dropExifThumbnail && hasThumb) data = ExifThumbnail.strip(data)
                    }
                    findings = findings.copy(
                        xmp = findings.xmp || isXmp,
                        iptc = findings.iptc || isIptc,
                        comment = findings.comment || isComment,
                        mpf = findings.mpf || isMpf,
                    )

                    val drop = sanitize && (isXmp || isIptc || isComment || isMpf)
                    if (!drop && out != null) {
                        out.write(0xFF); out.write(marker)
                        out.write(high); out.write(low); out.write(data)
                    }
                    if (marker == SOS) inScan = true
                }
            }
        }
    }

    /** Маркер вне энтропийных данных: 0xFF, затем (возможные fill-байты 0xFF) код маркера. */
    private fun readMarker(input: InputStream): Int {
        if (input.read() != 0xFF) throw IOException("Invalid JPEG marker")
        var marker = input.read()
        while (marker == 0xFF) marker = input.read()
        if (marker < 0) throw IOException("Truncated JPEG")
        if (marker == 0x00) throw IOException("Invalid JPEG marker")
        return marker
    }

    /**
     * Копирует энтропийные данные скана. 0xFF00 (byte stuffing) и RST0–7 относятся к данным.
     * Возвращает код первого настоящего маркера (DHT/SOS следующего скана, EOI и т. д.).
     */
    private fun copyEntropyUntilMarker(input: InputStream, out: OutputStream?): Int {
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("Truncated JPEG scan")
            if (b != 0xFF) { out?.write(b); continue }
            var next = input.read()
            while (next == 0xFF) next = input.read() // fill-байты
            when {
                next < 0 -> throw IOException("Truncated JPEG scan")
                next == 0x00 || next in 0xD0..0xD7 -> { out?.write(0xFF); out?.write(next) }
                else -> return next
            }
        }
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean {
        if (prefix.size > size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun readCompletely(input: InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count <= 0) throw IOException("Truncated JPEG segment")
            offset += count
        }
    }

    private const val BUF = 64 * 1024
}

/**
 * Работа с IFD1 (миниатюрой) внутри сегмента APP1 "Exif\0\0".
 * Миниатюра отключается обнулением указателя на IFD1 и затиранием её байтов и таблицы IFD1.
 * Длина сегмента не меняется, поэтому смещения остальных тегов остаются валидными.
 */
internal object ExifThumbnail {
    private const val TIFF = 6 // после "Exif\0\0"

    fun present(app1: ByteArray): Boolean = locateIfd1(app1) != null

    fun strip(app1: ByteArray): ByteArray {
        val ifd1 = locateIfd1(app1) ?: return app1
        val d = app1.copyOf()
        val r = Reader(d).also { it.init() }
        // 1. Затираем байты миниатюры (JPEG-миниатюра и/или одна полоса несжатой).
        val count = r.u16(ifd1.pos)
        var thumbOffset = -1L; var thumbLength = -1L
        var stripOffset = -1L; var stripLength = -1L
        for (i in 0 until count) {
            val e = ifd1.pos + 2 + i * 12
            if (e + 12 > d.size) break
            val tag = r.u16(e)
            val value = r.value(e)
            when (tag) {
                0x0201 -> thumbOffset = value
                0x0202 -> thumbLength = value
                0x0111 -> stripOffset = value
                0x0117 -> stripLength = value
            }
        }
        wipe(d, thumbOffset, thumbLength)
        wipe(d, stripOffset, stripLength)
        // 2. Затираем таблицу IFD1 и обнуляем указатель на неё из IFD0.
        val tableEnd = minOf(d.size.toLong(), ifd1.pos + 2L + count * 12L + 4L).toInt()
        for (i in ifd1.pos until tableEnd) d[i] = 0
        r.putU32(ifd1.pointerPos, 0)
        return d
    }

    private fun wipe(d: ByteArray, offset: Long, length: Long) {
        if (offset <= 0 || length <= 0) return
        val start = TIFF + offset
        val end = minOf(d.size.toLong(), start + length)
        if (start >= d.size) return
        for (i in start.toInt() until end.toInt()) d[i] = 0
    }

    private class Ifd1(val pos: Int, val pointerPos: Int)

    private fun locateIfd1(d: ByteArray): Ifd1? {
        if (d.size < TIFF + 8) return null
        val r = Reader(d)
        if (!r.init()) return null
        val ifd0 = TIFF + r.u32(TIFF + 4)
        if (ifd0 < TIFF + 8 || ifd0 + 2 > d.size) return null
        val n = r.u16(ifd0.toInt())
        val pointerPos = ifd0 + 2 + n * 12L
        if (pointerPos + 4 > d.size) return null
        val next = r.u32(pointerPos.toInt())
        if (next == 0L) return null
        val ifd1 = TIFF + next
        if (ifd1 + 2 > d.size) return null
        return Ifd1(ifd1.toInt(), pointerPos.toInt())
    }

    private class Reader(private val d: ByteArray) {
        private var little = false
        fun init(): Boolean {
            val a = d[TIFF].toInt(); val b = d[TIFF + 1].toInt()
            little = when {
                a == 'I'.code && b == 'I'.code -> true
                a == 'M'.code && b == 'M'.code -> false
                else -> return false
            }
            return u16(TIFF + 2) == 42
        }
        fun u8(o: Int) = d[o].toInt() and 0xFF
        fun u16(o: Int): Int = if (little) u8(o) or (u8(o + 1) shl 8) else (u8(o) shl 8) or u8(o + 1)
        fun u32(o: Int): Long = if (little) {
            (u8(o).toLong()) or (u8(o + 1).toLong() shl 8) or (u8(o + 2).toLong() shl 16) or (u8(o + 3).toLong() shl 24)
        } else {
            (u8(o).toLong() shl 24) or (u8(o + 1).toLong() shl 16) or (u8(o + 2).toLong() shl 8) or u8(o + 3).toLong()
        }
        /** Значение тега SHORT/LONG, лежащее прямо в записи (count = 1). */
        fun value(entry: Int): Long = when (u16(entry + 2)) {
            3 -> u16(entry + 8).toLong()
            4 -> u32(entry + 8)
            else -> -1L
        }
        fun putU32(o: Int, v: Long) {
            val bytes = if (little) {
                byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            } else {
                byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
            }
            bytes.copyInto(d, o)
        }
    }
}
