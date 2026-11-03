package com.example.metacheck.domain

import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.privacy.ExposureKind
import com.example.metacheck.domain.privacy.ExposureLevel
import com.example.metacheck.domain.privacy.PrivacyExposures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyExposureTest {

    private fun report(
        exif: List<MetaItem> = emptyList(),
        xmp: List<MetaItem> = emptyList(),
        png: List<MetaItem> = emptyList(),
        gps: GpsPoint? = null,
    ) = MetaReport(
        file = FileInfo("a.jpg", "image/jpeg", 10, ImageFormat.JPEG, 10, false),
        exifSections = listOf(MetaSection("", exif, SectionKey.CAMERA)),
        xmpItems = xmp,
        pngTextItems = png,
        gps = gps,
        signals = emptyList(),
    )

    private fun exif(tag: String, value: String) = MetaItem(tag, value, "exif:$tag")

    @Test fun cleanFileHasNoExposure() {
        val result = PrivacyExposures.of(report())
        assertEquals(0, result.score)
        assertEquals(ExposureLevel.NONE, result.level)
    }

    @Test fun phonePhotoWithGpsIsHighRiskAndShowsValues() {
        val result = PrivacyExposures.of(report(
            exif = listOf(
                exif("Make", "Apple"), exif("Model", "iPhone 15 Pro"),
                exif("DateTimeOriginal", "2024:03:12 18:42:00"), exif("BodySerialNumber", "F2LXK1"),
            ),
            gps = GpsPoint(41.902782, 12.496366, null),
        ))
        assertEquals(ExposureLevel.HIGH, result.level)
        assertEquals(ExposureKind.EXACT_LOCATION, result.items.first().kind)
        assertEquals("41.90278, 12.49637", result.items.first().value)
        val byKind = result.items.associate { it.kind to it.value }
        assertEquals("F2LXK1", byKind[ExposureKind.DEVICE_SERIAL])
        assertEquals("2024:03:12 18:42:00", byKind[ExposureKind.CAPTURE_TIME])
        assertEquals("Apple iPhone 15 Pro", byKind[ExposureKind.DEVICE])
    }

    @Test fun modelAlreadyContainingMakeIsNotDuplicated() {
        val result = PrivacyExposures.of(report(exif = listOf(exif("Make", "Canon"), exif("Model", "Canon EOS R6"))))
        assertEquals("Canon EOS R6", result.items.single { it.kind == ExposureKind.DEVICE }.value)
    }

    @Test fun authorFromExifAndXmpIsMergedAndScoreCapped() {
        val result = PrivacyExposures.of(report(
            exif = listOf(exif("Artist", "Ivan Petrov"), exif("CameraOwnerName", "Ivan"), exif("BodySerialNumber", "1"),
                exif("DateTimeOriginal", "x"), exif("ImageDescription", "home"), exif("Model", "X"), exif("Software", "Y")),
            xmp = listOf(MetaItem("dc:creator", "Ivan Petrov", "xmp:dc:creator"), MetaItem("dc:rights", "© Ivan", "xmp:dc:rights")),
            png = listOf(MetaItem("parameters", "a cat, Steps: 20")),
            gps = GpsPoint(1.0, 2.0, null),
        ))
        assertEquals(100, result.score)
        assertEquals("Ivan Petrov · © Ivan", result.items.single { it.kind == ExposureKind.AUTHOR }.value)
        assertTrue(result.items.any { it.kind == ExposureKind.AI_PROMPT })
    }

    @Test fun locationTagsWithoutCoordinatesAreReported() {
        val result = PrivacyExposures.of(report(xmp = listOf(MetaItem("exif:GPSLatitude", "41,54N", "xmp:exif:GPSLatitude"))))
        assertEquals(listOf(ExposureKind.LOCATION_TAGS), result.items.map { it.kind })
    }

    @Test fun ordinaryPngCommentIsNotAPrompt() {
        val result = PrivacyExposures.of(report(png = listOf(MetaItem("Comment", "Created with GIMP"))))
        assertTrue(result.items.none { it.kind == ExposureKind.AI_PROMPT })
    }

    @Test fun longValuesAreShortened() {
        val result = PrivacyExposures.of(report(exif = listOf(exif("ImageDescription", "x".repeat(500)))))
        assertEquals(80, result.items.single().value.length)
    }
}
