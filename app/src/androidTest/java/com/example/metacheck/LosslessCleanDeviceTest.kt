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

    private fun check(name: String, clean: (File, File) -> Unit) {
        val src = asset(name)
        val out = File(target.cacheDir, "clean_$name")
        try {
            assertNotNull("sample must have GPS", ExifInterface(src.absolutePath).latLong)
            clean(src, out)
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

    @Test fun heic() = check("gps_sample.heic", HeifCleaner::strip)

    @Test fun webp() = check("gps_sample.webp", WebpCleaner::strip)
}
