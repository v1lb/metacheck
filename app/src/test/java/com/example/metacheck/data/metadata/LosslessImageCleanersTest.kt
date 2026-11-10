package com.example.metacheck.data.metadata

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class LosslessImageCleanersTest {
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun be32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun be16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun chunk(type: String, data: ByteArray) =
        type.toByteArray(Charsets.ISO_8859_1) + le32(data.size) + data + if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf()

    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = "WEBP".toByteArray() + chunks.fold(ByteArray(0)) { a, b -> a + b }
        return "RIFF".toByteArray() + le32(body.size) + body
    }

    private fun temp(bytes: ByteArray) = File.createTempFile("lossless_", ".bin").apply { writeBytes(bytes) }

    @Test fun webpKeepsPixelsAndDropsExifXmpC2pa() {
        val pixels = ByteArray(33) { it.toByte() }
        val vp8x = byteArrayOf(0x0C, 0, 0, 0, 9, 0, 0, 9, 0, 0) // флаги EXIF|XMP
        val src = temp(webp(chunk("VP8X", vp8x), chunk("VP8 ", pixels), chunk("EXIF", "MM GPS".toByteArray()),
            chunk("XMP ", "<x/>".toByteArray()), chunk("C2PA", ByteArray(4))))
        val out = File.createTempFile("lossless_out_", ".webp")
        try {
            assertFalse(WebpCleaner.isClean(src))
            WebpCleaner.strip(src, out)
            assertTrue(WebpCleaner.isClean(out))
            val d = out.readBytes()
            val expected = webp(chunk("VP8X", vp8x.copyOf().also { it[0] = 0 }), chunk("VP8 ", pixels))
            assertArrayEquals(expected, d)
        } finally { src.delete(); out.delete() }
    }

    private fun box(type: String, payload: ByteArray) = be32(payload.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + payload

    /** ftyp + meta(hdlr, iinf[Exif, XMP], iloc) + uuid + mdat[exif, xmp, pixels]. */
    private fun heif(
        exif: ByteArray, xmp: ByteArray, pixels: ByteArray,
        extraBox: String = "uuid", pixelLength: Int = pixels.size, exifLength: Int = exif.size,
    ): Pair<ByteArray, Int> {
        val ftyp = box("ftyp", "heic".toByteArray() + be32(0) + "mif1heic".toByteArray())
        val hdlr = box("hdlr", ByteArray(4) + ByteArray(4) + "pict".toByteArray() + ByteArray(12) + byteArrayOf(0))
        fun infe(id: Int, type: String, extra: ByteArray) = box("infe", byteArrayOf(2, 0, 0, 0) + be16(id) + be16(0) + type.toByteArray() + byteArrayOf(0) + extra)
        val iinf = box("iinf", ByteArray(4) + be16(3) + infe(1, "Exif", byteArrayOf()) +
            infe(2, "mime", "application/rdf+xml".toByteArray() + byteArrayOf(0)) + infe(3, "hvc1", byteArrayOf()))
        fun iloc(base: Int): ByteArray {
            val entries = ByteArrayOutputStream()
            var off = base
            for ((id, real, stated) in listOf(Triple(1, exif.size, exifLength), Triple(2, xmp.size, xmp.size), Triple(3, pixels.size, pixelLength))) {
                entries.write(be16(id)); entries.write(be16(0)); entries.write(be16(1)); entries.write(be32(off)); entries.write(be32(stated))
                off += real
            }
            return box("iloc", byteArrayOf(0, 0, 0, 0, 0x44, 0x00) + be16(3) + entries.toByteArray())
        }
        val uuid = box(extraBox, ByteArray(16) + "c2pa manifest".toByteArray())
        // Длина meta от смещения не зависит, поэтому её можно посчитать заранее.
        val metaLen = box("meta", ByteArray(4) + hdlr + iinf + iloc(0)).size
        val mdatData = ftyp.size + metaLen + uuid.size + 8
        val meta = box("meta", ByteArray(4) + hdlr + iinf + iloc(mdatData))
        val file = ftyp + meta + uuid + box("mdat", exif + xmp + pixels)
        return file to mdatData
    }

    @Test fun heifBlanksMetadataInPlaceAndKeepsImage() {
        val exif = byteArrayOf(0, 0, 0, 6) + "Exif\u0000\u0000MM\u0000*GPS Pixel 8 serial F2LX".toByteArray()
        val xmp = "<x:xmpmeta><rdf:Description exif:GPSLatitude=\"48\"/></x:xmpmeta>".toByteArray()
        val pixels = ByteArray(40) { (it * 7).toByte() }
        val (bytes, mdatData) = heif(exif, xmp, pixels)
        val src = temp(bytes)
        val out = File.createTempFile("lossless_out_", ".heic")
        try {
            assertFalse(HeifCleaner.isClean(src))
            HeifCleaner.strip(src, out)
            val d = out.readBytes()
            assertEquals(bytes.size, d.size)
            assertTrue(HeifCleaner.isClean(out))
            val text = String(d, Charsets.ISO_8859_1)
            assertFalse(text.contains("GPS")); assertFalse(text.contains("serial")); assertFalse(text.contains("c2pa"))
            assertTrue(text.contains("free"))
            // Пиксели не тронуты.
            val px = mdatData + exif.size + xmp.size
            assertArrayEquals(pixels, d.copyOfRange(px, px + pixels.size))
            assertEquals(0x4D, d[mdatData + 4].toInt())
        } finally { src.delete(); out.delete() }
    }

    @Test fun motionVideoBoxIsBlankedAndOpenEndedImageExtentIsFine() {
        val exif = byteArrayOf(0, 0, 0, 0) + "MM\u0000*GPS here".toByteArray()
        val pixels = ByteArray(24) { (it + 1).toByte() }
        // Длина 0 у данных изображения по стандарту значит «до конца файла».
        val (bytes, mdatData) = heif(exif, "<x/>".toByteArray(), pixels, extraBox = "mpvd", pixelLength = 0)
        val src = temp(bytes)
        val out = File.createTempFile("lossless_out_", ".heic")
        try {
            HeifCleaner.strip(src, out)
            val text = String(out.readBytes(), Charsets.ISO_8859_1)
            assertFalse(text.contains("mpvd")); assertFalse(text.contains("c2pa")); assertFalse(text.contains("GPS"))
            val px = mdatData + exif.size + 4
            assertArrayEquals(pixels, out.readBytes().copyOfRange(px, px + pixels.size))
            assertTrue(HeifCleaner.isClean(out))
        } finally { src.delete(); out.delete() }
    }

    @Test fun metadataPointingOutsideTheFileIsRejectedAndNothingIsWritten() {
        val (bytes, _) = heif(ByteArray(20), "<x/>".toByteArray(), ByteArray(8), exifLength = 1_000_000)
        val src = temp(bytes)
        val out = File.createTempFile("lossless_out_", ".heic")
        try {
            assertTrue(runCatching { HeifCleaner.strip(src, out) }.exceptionOrNull() is java.io.IOException)
            assertEquals(bytes.size.toLong(), out.length())
        } finally { src.delete(); out.delete() }
    }

    @Test fun notHeifIsRejected() {
        val src = temp(ByteArray(64))
        val out = File.createTempFile("lossless_out_", ".heic")
        try {
            assertTrue(runCatching { HeifCleaner.strip(src, out) }.isFailure)
            assertTrue(runCatching { WebpCleaner.strip(src, out) }.isFailure)
        } finally { src.delete(); out.delete() }
    }
}
