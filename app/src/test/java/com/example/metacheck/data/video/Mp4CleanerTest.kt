package com.example.metacheck.data.video

import com.example.metacheck.data.parser.ImageFormatDetector
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.SectionKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Фикстуры собраны ffmpeg: android.mp4 (moov в конце, loci, ©nam, время создания),
 * iphone.mov (moov в начале, ключи com.apple.quicktime.*, дорожка таймкода tmcd).
 */
class Mp4CleanerTest {

    private fun fixture(name: String): ByteArray {
        val file = File("src/test/resources/video/$name")
        if (file.exists()) return file.readBytes()
        return javaClass.classLoader!!.getResourceAsStream("video/$name")!!.readBytes()
    }

    private fun clean(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { Mp4Cleaner.clean(ArrayByteSource(bytes), it) }.toByteArray()

    @Test
    fun detectsVideoContainersBySignature() {
        assertEquals(ImageFormat.MP4, ImageFormatDetector.detect(fixture("android.mp4")))
        assertEquals(ImageFormat.MOV, ImageFormatDetector.detect(fixture("iphone.mov")))
        // Canon CR3 и аудио M4A тоже ISO BMFF, но видео не являются.
        assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(ftyp("crx ")))
        assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(ftyp("M4A ")))
        assertEquals(ImageFormat.HEIC, ImageFormatDetector.detect(ftyp("heic")))
    }

    @Test
    fun readsIphoneTags() {
        val meta = Mp4MetadataReader.read(ArrayByteSource(fixture("iphone.mov")))
        assertEquals(ImageFormat.MOV, meta.format)
        assertEquals(48.8584, meta.gps!!.latitude, 1e-6)
        assertEquals(2.2945, meta.gps!!.longitude, 1e-6)
        val items = meta.sections.flatMap { it.items }
        assertEquals("Apple", items.first { it.key == "exif:Make" }.value)
        assertEquals("iPhone 15 Pro", items.first { it.key == "exif:Model" }.value)
        assertEquals("2026:10:02 08:00:00", items.first { it.key == Mp4MetadataReader.KEY_CREATED }.value)
        assertTrue(meta.toolFields.any { it.value == "18.1" })
        // Дорожка таймкода видна как скрытые данные.
        assertTrue(meta.sections.first { it.key == SectionKey.CONTAINER }.items.any { it.label.startsWith("trak tmcd") })
        assertTrue(meta.hasPersonalData)
    }

    @Test
    fun readsAndroidTags() {
        val meta = Mp4MetadataReader.read(ArrayByteSource(fixture("android.mp4")))
        assertEquals(ImageFormat.MP4, meta.format)
        assertEquals(41.8902, meta.gps!!.latitude, 1e-3)
        assertEquals(12.4923, meta.gps!!.longitude, 1e-3)
        val items = meta.sections.flatMap { it.items }
        assertEquals("Birthday", items.first { it.key == Mp4MetadataReader.KEY_TITLE }.value)
        assertEquals("0:01", items.first { it.key == Mp4MetadataReader.KEY_DURATION }.value)
        assertEquals("64", items.first { it.key == "exif:ImageWidth" }.value)
    }

    @Test
    fun cleanedFilesHaveNoPersonalDataAndSameMedia() {
        for (name in listOf("android.mp4", "iphone.mov")) {
            val original = fixture(name)
            val copy = original.copyOf()
            val cleaned = clean(copy)
            assertArrayEquals("source must stay untouched: $name", original, copy)
            assertTrue(name, Mp4Cleaner.isClean(ArrayByteSource(cleaned)))
            assertFalse(name, Mp4Cleaner.isClean(ArrayByteSource(original)))
            val after = Mp4MetadataReader.read(ArrayByteSource(cleaned))
            assertNull(after.gps)
            assertFalse(after.hasPersonalData)
            assertEquals(listOf(SectionKey.IMAGE), after.sections.map { it.key })
            assertSameSamples(original, cleaned, name)
        }
    }

    @Test
    fun outputViewMatchesWrittenBytes() {
        val src = ArrayByteSource(fixture("iphone.mov"))
        val plan = Mp4Cleaner.plan(src)
        val view = Mp4Cleaner.output(plan)
        val written = ByteArrayOutputStream().also { Mp4Cleaner.write(plan, it) }.toByteArray()
        assertEquals(plan.outputBytes, view.size)
        assertArrayEquals(written, view.bytes(0, view.size.toInt()))
        // Чтение кусками через границы боксов даёт те же байты.
        val chunks = ByteArrayOutputStream()
        var pos = 0L
        while (pos < view.size) {
            val n = minOf(777L, view.size - pos).toInt()
            chunks.write(view.bytes(pos, n)); pos += n
        }
        assertArrayEquals(written, chunks.toByteArray())
        assertEquals(1, plan.removedTracks)
    }

    @Test
    fun removedTrackDataIsZeroed() {
        val original = fixture("iphone.mov")
        val cleaned = clean(original)
        val tmcd = trackChunks(original).getValue("tmcd")
        // Таймкод (время съёмки 10:20:30) был в mdat: в копии на этом месте нули.
        val mdatShift = mdatPayload(cleaned) - mdatPayload(original)
        for ((offset, length) in tmcd) {
            val at = (offset + mdatShift).toInt()
            assertTrue(original.copyOfRange(offset.toInt(), offset.toInt() + length).any { it.toInt() != 0 })
            assertTrue(cleaned.copyOfRange(at, at + length).all { it.toInt() == 0 })
        }
        assertFalse(trackChunks(cleaned).containsKey("tmcd"))
    }

    @Test
    fun cleaningIsIdempotent() {
        val once = clean(fixture("android.mp4"))
        val twice = clean(once)
        assertArrayEquals(once, twice)
    }

    @Test
    fun rejectsTruncatedAndFragmentedFiles() {
        val original = fixture("android.mp4")
        // moov в конце файла: обрезанный файл без moov не очищается.
        expectUnsupported(original.copyOf(original.size / 2))
        // Обрезанный mdat в файле с moov в начале: смещения указывают за пределы данных.
        val mov = fixture("iphone.mov")
        expectUnsupported(mov.copyOf(mov.size - 2000))
        expectUnsupported(ftyp("isom") + box("moov", box("mvhd", ByteArray(100))) + box("moof", ByteArray(8)) + box("mdat", ByteArray(8)))
    }

    @Test
    fun parsesQuickTimeLocationAndKeysInMoov() {
        // Android пишет moov/udta/©xyz, iPhone — moov/meta с ключами mdta.
        val xyzText = "+59.9386+030.3141/".toByteArray()
        val xyz = box("©xyz", byteArrayOf(0, xyzText.size.toByte(), 0x15, 0xC7.toByte()) + xyzText)
        val key = "com.apple.quicktime.model".toByteArray()
        val keys = box("keys", u32(0) + u32(1) + u32(8L + key.size) + "mdta".toByteArray() + key)
        val value = "iPhone 16".toByteArray()
        val ilst = box("ilst", boxRaw(u32(1), box("data", u32(1) + u32(0) + value)))
        val meta = box("meta", box("hdlr", ByteArray(24).also { "mdta".toByteArray().copyInto(it, 8) }) + keys + ilst)
        val moov = box("moov", box("udta", xyz) + meta)
        val meta2 = Mp4MetadataReader.read(ArrayByteSource(ftyp("qt  ") + moov))
        assertEquals(59.9386, meta2.gps!!.latitude, 1e-6)
        assertEquals(30.3141, meta2.gps!!.longitude, 1e-6)
        assertEquals("iPhone 16", meta2.sections.flatMap { it.items }.first { it.key == "exif:Model" }.value)
    }

    @Test
    fun parsesIso6709Variants() {
        val dec = Mp4MetadataReader.parseIso6709("+41.8902+012.4923+050.000/")!!
        assertEquals(41.8902, dec.latitude, 1e-9); assertEquals(12.4923, dec.longitude, 1e-9); assertEquals(50.0, dec.altitudeMeters!!, 1e-9)
        val dm = Mp4MetadataReader.parseIso6709("+4153.412+01229.538/")!!
        assertEquals(41 + 53.412 / 60, dm.latitude, 1e-9); assertEquals(12 + 29.538 / 60, dm.longitude, 1e-9)
        val neg = Mp4MetadataReader.parseIso6709("-33.8688+151.2093/")!!
        assertEquals(-33.8688, neg.latitude, 1e-9)
        assertNull(Mp4MetadataReader.parseIso6709("+00.0000+000.0000/")) // обнулённое системой место
        assertNull(Mp4MetadataReader.parseIso6709("+95.0+010.0/"))
        assertNull(Mp4MetadataReader.parseIso6709("garbage"))
    }

    @Test
    fun malformedInputDoesNotCrashReader() {
        val original = fixture("iphone.mov")
        val random = java.util.Random(7)
        repeat(300) {
            val damaged = original.copyOf()
            repeat(8) { damaged[random.nextInt(damaged.size)] = random.nextInt(256).toByte() }
            try {
                Mp4MetadataReader.read(ArrayByteSource(damaged))
            } catch (_: Mp4FormatException) {
            } catch (_: java.io.EOFException) {
            }
            // Очистка повреждённого файла либо отказывает, либо даёт чистый файл.
            val out = try { clean(damaged) } catch (_: Mp4FormatException) { null } catch (_: java.io.EOFException) { null }
            if (out != null) assertTrue(Mp4Cleaner.isClean(ArrayByteSource(out)))
        }
    }

    // --- помощники ---

    private fun expectUnsupported(bytes: ByteArray) {
        try {
            clean(bytes); fail("expected Mp4FormatException")
        } catch (_: Mp4FormatException) {
        }
    }

    /** Байты каждого фрагмента оставшихся дорожек совпадают с исходными. */
    private fun assertSameSamples(original: ByteArray, cleaned: ByteArray, name: String) {
        val before = trackChunks(original)
        val after = trackChunks(cleaned)
        for ((handler, chunks) in after) {
            val old = before.getValue(handler)
            assertEquals(name, old.size, chunks.size)
            for (i in chunks.indices) {
                val (o, len) = old[i]
                val (n, len2) = chunks[i]
                assertEquals(len, len2)
                assertArrayEquals("$name $handler chunk $i", original.copyOfRange(o.toInt(), o.toInt() + len), cleaned.copyOfRange(n.toInt(), n.toInt() + len2))
            }
        }
    }

    /** handler → (смещение, длина) фрагментов, по stco/stsc/stsz. */
    private fun trackChunks(file: ByteArray): Map<String, List<Pair<Long, Int>>> {
        val src = ArrayByteSource(file)
        val moov = IsoBmff.list(src, 0, src.size, lenient = true).first.first { it.type == "moov" }
        val result = LinkedHashMap<String, List<Pair<Long, Int>>>()
        for (trak in IsoBmff.children(src, moov).filter { it.type == "trak" }) {
            val info = Mp4MetadataReader.trackInfo(src, trak)!!
            val mdia = IsoBmff.children(src, trak).first { it.type == "mdia" }
            val minf = IsoBmff.children(src, mdia).first { it.type == "minf" }
            val stbl = IsoBmff.children(src, minf).first { it.type == "stbl" }
            val boxes = IsoBmff.children(src, stbl).associateBy { it.type }
            val co = src.bytes(boxes.getValue("stco").payloadStart, boxes.getValue("stco").payloadSize.toInt())
            val sz = src.bytes(boxes.getValue("stsz").payloadStart, boxes.getValue("stsz").payloadSize.toInt())
            val sc = src.bytes(boxes.getValue("stsc").payloadStart, boxes.getValue("stsc").payloadSize.toInt())
            val offsets = (0 until IsoBmff.u32(co, 4).toInt()).map { IsoBmff.u32(co, 8 + it * 4) }
            val fixed = IsoBmff.u32(sz, 4)
            val entries = IsoBmff.u32(sc, 4).toInt()
            var sample = 0
            val chunks = ArrayList<Pair<Long, Int>>()
            for (e in 0 until entries) {
                val first = IsoBmff.u32(sc, 8 + e * 12).toInt()
                val per = IsoBmff.u32(sc, 12 + e * 12).toInt()
                val next = if (e + 1 < entries) IsoBmff.u32(sc, 8 + (e + 1) * 12).toInt() else offsets.size + 1
                for (c in first until next) {
                    var len = 0L
                    repeat(per) { len += if (fixed != 0L) fixed else IsoBmff.u32(sz, 12 + sample * 4); sample++ }
                    chunks += offsets[c - 1] to len.toInt()
                }
            }
            result[info.handler] = chunks
        }
        return result
    }

    private fun mdatPayload(file: ByteArray): Long {
        val src = ArrayByteSource(file)
        return IsoBmff.list(src, 0, src.size, lenient = true).first.first { it.type == "mdat" }.payloadStart
    }

    private fun u32(v: Long) = ByteArray(4).also { IsoBmff.putU32(it, 0, v) }
    private fun u32(v: Int) = u32(v.toLong())
    private fun box(type: String, payload: ByteArray) = boxRaw(type.toByteArray(Charsets.ISO_8859_1), payload)
    private fun boxRaw(type: ByteArray, payload: ByteArray) = u32(8L + payload.size) + type + payload
    private fun ftyp(brand: String) = box("ftyp", brand.toByteArray(Charsets.ISO_8859_1) + u32(0) + brand.toByteArray(Charsets.ISO_8859_1))
}
