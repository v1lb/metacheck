package com.example.metacheck

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.metacheck.data.metadata.HeifCleaner
import com.example.metacheck.data.metadata.WebpCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Очистка HEIC и WebP без пережатия на настоящем декодере Android: GPS пропадает, картинка открывается. */
@RunWith(AndroidJUnit4::class)
class LosslessCleanDeviceTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun asset(name: String): File = File(target.cacheDir, name).also { f ->
        testAssets.open(name).use { input -> f.outputStream().use { input.copyTo(it) } }
    }

    /** [exifGps]: Android ExifInterface читает GPS из образца (для WebP от exiftool — нет, проверяем блоки). */
    private fun check(name: String, exifGps: Boolean, isClean: (File) -> Boolean, clean: (File, File) -> Unit) {
        val src = asset(name)
        val out = File(target.cacheDir, "clean_$name")
        try {
            if (exifGps) assertNotNull("sample must have GPS", ExifInterface(src.absolutePath).latLong)
            assertTrue(!isClean(src))
            clean(src, out)
            assertTrue(isClean(out))
            assertNull(ExifInterface(out.absolutePath).latLong)
            val original = BitmapFactory.decodeFile(src.absolutePath)
            val cleaned = BitmapFactory.decodeFile(out.absolutePath)
            if (original != null) {
                assertNotNull(cleaned)
                assertEquals(original.width, cleaned!!.width)
                assertEquals(original.height, cleaned.height)
                assertTrue(original.sameAs(cleaned))
            }
        } finally { src.delete(); out.delete() }
    }

    /** «Удалить только GPS» у HEIC: места нет, камера и картинка на месте. */
    @Test fun heicGpsOnly() {
        val src = asset("gps_sample.heic")
        val out = File(target.cacheDir, "gpsfree.heic")
        try {
            HeifCleaner.stripGps(src, out)
            val exif = ExifInterface(out.absolutePath)
            assertNull(exif.latLong)
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertEquals("Apple", exif.getAttribute(ExifInterface.TAG_MAKE))
            val original = BitmapFactory.decodeFile(src.absolutePath)
            val cleaned = BitmapFactory.decodeFile(out.absolutePath)
            if (original != null) assertTrue(original.sameAs(cleaned))
        } finally { src.delete(); out.delete() }
    }

    @Test fun heic() = check("gps_sample.heic", exifGps = true, HeifCleaner::isClean, HeifCleaner::strip)

    @Test fun webp() = check("gps_sample.webp", exifGps = false, WebpCleaner::isClean, WebpCleaner::strip)
}
