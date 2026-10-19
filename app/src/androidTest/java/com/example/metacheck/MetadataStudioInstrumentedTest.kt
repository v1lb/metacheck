package com.example.metacheck

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.metacheck.data.metadata.MetadataRewriteRepositoryImpl
import com.example.metacheck.domain.metadata.MetadataPatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Execute on Android hardware/emulator: verifies real ExifInterface and FileProvider writes. */
@RunWith(AndroidJUnit4::class)
class MetadataStudioInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val cache get() = File(context.cacheDir, "camera").apply { mkdirs() }
    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    private fun sampleJpeg(file: File) {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(44, 66, 88))
        try { FileOutputStream(file).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) } }
        finally { bitmap.recycle() }
        ExifInterface(file.absolutePath).apply {
            setLatLong(41.9028, 12.4964)
            setAttribute(ExifInterface.TAG_MODEL, "Legacy Camera")
            saveAttributes()
        }
    }

    @Test fun editCopyWritesExifAndCanRemoveGps() = runBlocking {
        val original = File(cache, "edit_source_${System.nanoTime()}.jpg")
        val target = File(cache, "edit_target_${System.nanoTime()}.jpg")
        try {
            sampleJpeg(original)
            val result = MetadataRewriteRepositoryImpl(context).editCopy(
                uri(original).toString(), uri(target).toString(),
                MetadataPatch(removeGps = true, model = "MetaCheck Test", iso = 200),
            )
            assertTrue(result.verified)
            val exif = ExifInterface(target.absolutePath)
            assertNull(exif.latLong)
            assertEquals("MetaCheck Test", exif.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("Legacy Camera", ExifInterface(original.absolutePath).getAttribute(ExifInterface.TAG_MODEL))
            assertNotNull(ExifInterface(original.absolutePath).latLong)
        } finally { original.delete(); target.delete() }
    }

    @Test fun wipeGpsCopyKeepsCameraTagAndProtectsOriginal() = runBlocking {
        val original = File(cache, "gps_source_${System.nanoTime()}.jpg")
        val target = File(cache, "gps_safe_${System.nanoTime()}.jpg")
        try {
            sampleJpeg(original)
            val result = MetadataRewriteRepositoryImpl(context).wipeGpsCopy(uri(original).toString(), uri(target).toString())
            assertTrue(result.verified)
            assertFalse(result.downscaled)
            val cleaned = ExifInterface(target.absolutePath)
            assertNull(cleaned.latLong)
            assertNull(cleaned.getAttribute(ExifInterface.TAG_GPS_ALTITUDE))
            assertEquals("Legacy Camera", cleaned.getAttribute(ExifInterface.TAG_MODEL))
            assertNotNull(ExifInterface(original.absolutePath).latLong)
        } finally { original.delete(); target.delete() }
    }

    @Test fun stripCopyClearsExifAndPreservesOriginal() = runBlocking {
        val original = File(cache, "strip_source_${System.nanoTime()}.jpg")
        val target = File(cache, "strip_target_${System.nanoTime()}.jpg")
        try {
            sampleJpeg(original)
            val result = MetadataRewriteRepositoryImpl(context).stripCopy(uri(original).toString(), uri(target).toString())
            assertTrue(result.verified)
            assertNull(ExifInterface(target.absolutePath).latLong)
            assertNull(ExifInterface(target.absolutePath).getAttribute(ExifInterface.TAG_MODEL))
            assertNotNull(ExifInterface(original.absolutePath).latLong)
        } finally { original.delete(); target.delete() }
    }

    @Test fun backupIsCreatedBeforeOverwrite() = runBlocking {
        val original = File(cache, "overwrite_source_${System.nanoTime()}.jpg")
        val backup = File(cache, "overwrite_backup_${System.nanoTime()}.jpg")
        try {
            sampleJpeg(original)
            val result = MetadataRewriteRepositoryImpl(context).overwriteWithBackup(
                uri(original).toString(), uri(backup).toString(), null,
            )
            assertTrue(result.verified)
            assertNull(ExifInterface(original.absolutePath).latLong)
            assertNotNull(ExifInterface(backup.absolutePath).latLong)
        } finally { original.delete(); backup.delete() }
    }
}
