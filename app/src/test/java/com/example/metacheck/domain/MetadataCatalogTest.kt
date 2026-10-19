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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCatalogTest {
    private val report = MetaReport(
        file = FileInfo("photo.jpg", "image/jpeg", 1000, ImageFormat.JPEG, 1000, false),
        exifSections = listOf(MetaSection("Камера и объектив", listOf(MetaItem("Модель", "Samsung"))),
            MetaSection("Даты", listOf(MetaItem("Дата съёмки", "2026:10:19 13:00")))),
        xmpItems = listOf(MetaItem("CreatorTool", "=SUM(A1:A2)")),
        pngTextItems = emptyList(), gps = GpsPoint(41.9028, 12.4964, null), signals = emptyList(),
    )

    @Test fun catalogContainsAllExtractedFieldsAndGps() {
        val records = MetadataCatalog.from(report)
        assertTrue(records.any { it.group == MetadataGroup.MAIN && it.label == "Имя" })
        assertTrue(records.any { it.group == MetadataGroup.CAMERA && it.value == "Samsung" })
        assertTrue(records.any { it.group == MetadataGroup.LOCATION && it.label == "Широта" })
        assertTrue(records.any { it.group == MetadataGroup.XMP && it.value.startsWith("=SUM") })
    }

    @Test fun searchMatchesTagsValuesAndCategory() {
        val records = MetadataCatalog.from(report)
        assertEquals(1, MetadataCatalog.filter(records, MetadataGroup.CAMERA, "samSUNG").size)
        assertEquals(1, MetadataCatalog.filter(records, null, "дата съёмки").size)
        assertEquals(0, MetadataCatalog.filter(records, MetadataGroup.LOCATION, "samsung").size)
    }

    @Test fun csvEscapesFormulaAndKeepsUtf8AndQuotes() {
        val csv = String(MetadataCsv.encode(MetadataCatalog.from(report)), Charsets.UTF_8)
        assertTrue(csv.startsWith("\uFEFFcategory"))
        assertTrue(csv.contains("\"'=SUM(A1:A2)\""))
        assertTrue(csv.contains("\"Samsung\""))
    }
}
