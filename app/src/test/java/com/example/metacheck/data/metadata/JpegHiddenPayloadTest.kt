package com.example.metacheck.data.metadata

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Регрессия аудита, п. 2: «удаление GPS» не должно оставлять координаты в скрытых частях JPEG. */
class JpegHiddenPayloadTest {

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun seg(marker: Int, data: ByteArray): ByteArray {
        val len = data.size + 2
        return b(0xFF, marker, len shr 8, len and 0xFF) + data
    }
    private val sof = seg(0xC0, b(8, 0, 1, 0, 1, 1, 1, 0x11, 0))
    private val sos = seg(0xDA, b(1, 1, 0, 0, 0x3F, 0))
    /** Энтропийные данные с byte stuffing (FF00), RST и fill-байтами. */
    private val entropy = b(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD0, 0x56, 0xFF, 0xD7, 0x78)
    private val motionPhoto = "\u0000\u0000\u0000\u0018ftypmp42....©xyz+41.9028+012.4964/".toByteArray(Charsets.ISO_8859_1)

    private fun jpeg(vararg parts: ByteArray) = ByteArrayOutputStream().apply {
        write(b(0xFF, 0xD8)); parts.forEach { write(it) }
    }.toByteArray()

    private fun clean(bytes: ByteArray, dropThumb: Boolean = false): ByteArray {
        val src = File.createTempFile("jh_src", ".jpg"); val dst = File.createTempFile("jh_dst", ".jpg")
        try {
            src.writeBytes(bytes)
            JpegSegments.removeContradictorySegments(src, dst, dropThumb)
            return dst.readBytes()
        } finally { src.delete(); dst.delete() }
    }
    private fun inspect(bytes: ByteArray): JpegSegments.Findings {
        val f = File.createTempFile("jh_in", ".jpg")
        try { f.writeBytes(bytes); return JpegSegments.inspect(f) } finally { f.delete() }
    }
    private fun ByteArray.text() = toString(Charsets.ISO_8859_1)

    @Test fun motionPhotoTrailerAfterEoiIsRemoved() {
        val main = jpeg(sof, sos, entropy, b(0xFF, 0xD9))
        val source = main + motionPhoto
        assertTrue(inspect(source).trailer)

        val out = clean(source)

        assertArrayEquals(main, out)
        assertFalse(out.text().contains("ftyp"))
        assertFalse(inspect(out).trailer)
    }

    @Test fun mpfSecondaryImagesAndGainmapAreRemoved() {
        val mpf = seg(0xE2, "MPF\u0000II*\u0000....".toByteArray(Charsets.ISO_8859_1))
        val icc = seg(0xE2, "ICC_PROFILE\u0000\u0001\u0001data".toByteArray(Charsets.ISO_8859_1))
        val secondary = jpeg(seg(0xE1, "Exif\u0000\u0000GPS-of-gainmap".toByteArray()), sos, entropy, b(0xFF, 0xD9))
        val source = jpeg(icc, mpf, sof, sos, entropy, b(0xFF, 0xD9)) + secondary

        val found = inspect(source)
        assertTrue(found.mpf && found.trailer)

        val out = clean(source).text()
        assertTrue("ICC профиль нужен для цветов", out.contains("ICC_PROFILE"))
        assertFalse(out.contains("MPF"))
        assertFalse(out.contains("GPS-of-gainmap"))
    }

    @Test fun progressiveScansAndStuffingArePreservedByteForByte() {
        val dht = seg(0xC4, b(0x00, 1, 2, 3))
        val source = jpeg(sof, sos, entropy, dht, sos, entropy, seg(0xDD, b(0, 4)), sos, entropy, b(0xFF, 0xD9))
        assertArrayEquals(source, clean(source))
    }

    @Test fun xmpIptcCommentStillRemovedExifKept() {
        val source = jpeg(
            seg(0xE1, "Exif\u0000\u0000camera".toByteArray()),
            seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<exif:GPSLatitude>".toByteArray()),
            seg(0xED, "Photoshop 3.0\u0000iptc".toByteArray()),
            seg(0xFE, "comment".toByteArray()),
            sof, sos, entropy, b(0xFF, 0xD9),
        )
        val out = clean(source)
        val f = inspect(out)
        assertTrue(f.exif)
        assertFalse(f.xmp || f.iptc || f.comment)
    }

    @Test fun strippedCheckRejectsTrailerAndMpf() {
        val plain = jpeg(sof, sos, entropy, b(0xFF, 0xD9))
        val withTrailer = File.createTempFile("jh_t", ".jpg"); val ok = File.createTempFile("jh_t", ".jpg")
        try {
            ok.writeBytes(plain); withTrailer.writeBytes(plain + motionPhoto)
            assertTrue(JpegSegments.isStrippedJpeg(ok))
            assertFalse(JpegSegments.isStrippedJpeg(withTrailer))
        } finally { ok.delete(); withTrailer.delete() }
    }

    @Test fun truncatedScanIsRejectedNotSilentlyAccepted() {
        val src = jpeg(sof, sos, entropy) // нет EOI
        val f = File.createTempFile("jh_t", ".jpg")
        try { f.writeBytes(src); assertFalse(JpegSegments.isStrippedJpeg(f)) } finally { f.delete() }
    }

    // --- миниатюра EXIF (IFD1) ---

    /** APP1 Exif: IFD0 с одним тегом (Make), IFD1 с JPEGInterchangeFormat/Length, затем байты миниатюры. */
    private fun exifWithThumbnail(little: Boolean): ByteArray {
        fun u16(v: Int) = if (little) b(v and 0xFF, v shr 8) else b(v shr 8, v and 0xFF)
        fun u32(v: Int) = if (little) b(v and 0xFF, (v shr 8) and 0xFF, (v shr 16) and 0xFF, v ushr 24)
            else b(v ushr 24, (v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        val thumb = "THUMB-ORIGINAL-UNCROPPED".toByteArray()
        val ifd0 = 8
        val ifd1 = ifd0 + 2 + 12 + 4
        val thumbOffset = ifd1 + 2 + 2 * 12 + 4
        val tiff = ByteArrayOutputStream().apply {
            write(if (little) "II".toByteArray() else "MM".toByteArray()); write(u16(42)); write(u32(ifd0))
            write(u16(1)); write(u16(0x010F)); write(u16(3)); write(u32(1)); write(u16(1)); write(u16(0))
            write(u32(ifd1))
            write(u16(2))
            write(u16(0x0201)); write(u16(4)); write(u32(1)); write(u32(thumbOffset))
            write(u16(0x0202)); write(u16(4)); write(u32(1)); write(u32(thumb.size))
            write(u32(0))
            write(thumb)
        }.toByteArray()
        return "Exif\u0000\u0000".toByteArray() + tiff
    }

    @Test fun exifThumbnailIsDroppedOnGpsWipe_littleAndBigEndian() {
        for (little in listOf(true, false)) {
            val app1 = exifWithThumbnail(little)
            assertTrue(ExifThumbnail.present(app1))
            val source = jpeg(seg(0xE1, app1), sof, sos, entropy, b(0xFF, 0xD9))
            assertTrue(inspect(source).exifThumbnail)

            val out = clean(source, dropThumb = true)

            assertEquals("длина сегмента не меняется", source.size, out.size)
            assertFalse(out.text().contains("THUMB-ORIGINAL"))
            val f = inspect(out)
            assertTrue(f.exif)
            assertFalse(f.exifThumbnail)
            assertFalse(f.hasHiddenPayload)
        }
    }

    @Test fun exifThumbnailKeptWhenNotWipingGps() {
        val source = jpeg(seg(0xE1, exifWithThumbnail(true)), sof, sos, entropy, b(0xFF, 0xD9))
        assertTrue(clean(source, dropThumb = false).text().contains("THUMB-ORIGINAL"))
    }

    @Test fun malformedExifDoesNotCrash() {
        val bad = "Exif\u0000\u0000II*\u0000ÿÿÿ\u007F".toByteArray(Charsets.ISO_8859_1)
        assertFalse(ExifThumbnail.present(bad))
        assertArrayEquals(bad, ExifThumbnail.strip(bad))
    }
}
