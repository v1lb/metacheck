package com.example.metacheck.domain

import com.example.metacheck.domain.export.MetadataCsv
import com.example.metacheck.domain.metadata.MetadataCatalog
import com.example.metacheck.domain.metadata.MetadataGroup
import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.testutil.FakeTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCatalogTest {
    private val report = MetaReport(
        file = FileInfo("photo.jpg", "image/jpeg", 1000, ImageFormat.JPEG, 1000, false),
        exifSections = listOf(
            MetaSection("", listOf(MetaItem("Model", "Samsung", key = "exif:Model")), key = SectionKey.CAMERA),
            MetaSection("", listOf(MetaItem("DateTimeOriginal", "2026:10:19 13:00", key = "exif:DateTimeOriginal")),
                key = SectionKey.DATES),
        ),
        xmpItems = listOf(MetaItem("CreatorTool", "=SUM(A1:A2)", key = "xmp:xmp:CreatorTool")),
        pngTextItems = emptyList(), gps = GpsPoint(41.9028, 12.4964, null), signals = emptyList(),
    )

    /** Как будто интерфейс на русском: подписи локализованы, ключи остаются английскими. */
    private val russianUi = FakeTexts(mapOf("exif:Model" to "Модель", "exif:DateTimeOriginal" to "Дата съёмки"))

    @Test fun catalogContainsAllExtractedFieldsAndGps() {
        val records = MetadataCatalog.from(report, FakeTexts())
        assertTrue(records.any { it.group == MetadataGroup.MAIN && it.key == "file:name" && it.value == "photo.jpg" })
        assertTrue(records.any { it.group == MetadataGroup.CAMERA && it.value == "Samsung" })
        assertTrue(records.any { it.group == MetadataGroup.MAIN && it.key == "exif:DateTimeOriginal" })
        assertTrue(records.any { it.group == MetadataGroup.LOCATION && it.key == "gps:latitude" && it.value == "41.9028" })
        assertTrue(records.any { it.group == MetadataGroup.XMP && it.value.startsWith("=SUM") })
    }

    @Test fun labelsAndGroupTitlesComeFromTexts() {
        val records = MetadataCatalog.from(report, russianUi)
        assertEquals("Модель", records.single { it.key == "exif:Model" }.label)
        assertTrue(records.all { it.groupTitle == "group:${it.group.name}" })
    }

    @Test fun searchMatchesTagsValuesAndCategory() {
        val records = MetadataCatalog.from(report, russianUi)
        assertEquals(1, MetadataCatalog.filter(records, MetadataGroup.CAMERA, "samSUNG").size)
        assertEquals(1, MetadataCatalog.filter(records, null, "дата съёмки").size)
        assertEquals(0, MetadataCatalog.filter(records, MetadataGroup.LOCATION, "samsung").size)
    }

    @Test fun englishSearchWorksWhateverTheInterfaceLanguage() {
        val records = MetadataCatalog.from(report, russianUi)
        assertEquals(1, MetadataCatalog.filter(records, MetadataGroup.CAMERA, "model").size)
        assertEquals(1, MetadataCatalog.filter(records, null, "datetimeoriginal").size)
        assertTrue(MetadataCatalog.filter(records, null, "latitude").any { it.key == "gps:latitude" })
        assertTrue(MetadataCatalog.filter(records, null, "shutter").all { it.group == MetadataGroup.CAMERA })
    }

    @Test fun csvEscapesFormulaAndKeepsUtf8AndQuotes() {
        val csv = String(MetadataCsv.encode(MetadataCatalog.from(report, russianUi)), Charsets.UTF_8)
        assertTrue(csv.startsWith("﻿category"))
        assertTrue(csv.contains("\"'=SUM(A1:A2)\""))
        assertTrue(csv.contains("\"Samsung\""))
        assertTrue(csv.contains("\"Модель\""))
    }
}
