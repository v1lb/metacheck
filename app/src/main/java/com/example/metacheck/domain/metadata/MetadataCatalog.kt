package com.example.metacheck.domain.metadata

import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.text.MetaTexts
import com.example.metacheck.domain.text.TextKey
import java.util.Locale

/** Группа каталога. [marker] — короткая техническая метка, одинаковая во всех языках. */
enum class MetadataGroup(val marker: String) {
    MAIN("FILE"),
    CAMERA("CAM"),
    LOCATION("GPS"),
    EXIF("EXIF"),
    XMP("XMP"),
    PNG("PNG"),
    VIDEO("MP4"),
}

/**
 * Строка каталога с уже локализованными подписями. [key] — стабильный ключ поля (имя тега),
 * по нему работает поиск на английском независимо от языка интерфейса.
 */
data class MetadataRecord(
    val group: MetadataGroup,
    val groupTitle: String,
    val section: String,
    val label: String,
    val value: String,
    val key: String? = null,
)

/** Index of all metadata fields that the current on-device parsers have extracted.
 * Neither infers nonexistent tags nor invents a confidence score. */
object MetadataCatalog {
    fun from(report: MetaReport, texts: MetaTexts): List<MetadataRecord> = buildList {
        fun record(group: MetadataGroup, section: String, label: String, value: String, key: String?) =
            add(MetadataRecord(group, texts.groupTitle(group), section, label, value, key))
        fun items(group: MetadataGroup, section: String, entries: List<MetaItem>) =
            entries.forEach { record(group, section, texts.itemLabel(it), texts.itemValue(it), it.key) }

        val file = report.file
        val fileSection = texts.text(TextKey.CATALOG_FILE_SECTION)
        record(MetadataGroup.MAIN, fileSection, texts.text(TextKey.FIELD_NAME),
            file.name.ifBlank { texts.text(TextKey.UNNAMED) }, "file:name")
        record(MetadataGroup.MAIN, fileSection, texts.text(TextKey.FIELD_FORMAT), texts.formatName(file.format), "file:format")
        record(MetadataGroup.MAIN, fileSection, texts.text(TextKey.FIELD_MIME),
            file.mimeType.ifBlank { texts.text(TextKey.UNKNOWN) }, "file:mime")
        record(MetadataGroup.MAIN, fileSection, texts.text(TextKey.FIELD_SIZE_BYTES), file.sizeBytes.toString(), "file:size")
        record(MetadataGroup.MAIN, fileSection, texts.text(TextKey.FIELD_SCANNED_BYTES), file.scannedBytes.toString(), "file:scanned")
        record(MetadataGroup.MAIN, fileSection, texts.text(TextKey.FIELD_PARTIAL),
            texts.text(if (file.truncated) TextKey.YES else TextKey.NO), "file:partial")

        for (section in report.exifSections) {
            val group = when (section.key) {
                SectionKey.CAMERA, SectionKey.EXPOSURE -> MetadataGroup.CAMERA
                SectionKey.DATES -> MetadataGroup.MAIN
                SectionKey.GPS -> MetadataGroup.LOCATION
                SectionKey.CONTAINER -> MetadataGroup.VIDEO
                else -> MetadataGroup.EXIF
            }
            items(group, texts.sectionTitle(section), section.items)
        }
        report.gps?.let { gps ->
            val coordinates = texts.text(TextKey.CATALOG_COORDINATES_SECTION)
            record(MetadataGroup.LOCATION, coordinates, texts.text(TextKey.FIELD_LATITUDE), gps.latitude.toString(), "gps:latitude")
            record(MetadataGroup.LOCATION, coordinates, texts.text(TextKey.FIELD_LONGITUDE), gps.longitude.toString(), "gps:longitude")
            gps.altitudeMeters?.let {
                record(MetadataGroup.LOCATION, coordinates, texts.text(TextKey.FIELD_ALTITUDE_M), it.toString(), "gps:altitude")
            }
        }
        items(MetadataGroup.XMP, texts.text(TextKey.XMP_IPTC), report.xmpItems)
        items(MetadataGroup.PNG, texts.text(TextKey.PNG_TEXT), report.pngTextItems)
    }

    /**
     * Поиск без учёта регистра по подписи, значению, разделу и группе на языке интерфейса,
     * а также по техническому ключу поля и английским синонимам группы — так английские
     * запросы («make», «gps», «shutter») работают при любом языке интерфейса.
     */
    fun filter(items: List<MetadataRecord>, group: MetadataGroup?, query: String): List<MetadataRecord> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return items.filter { item ->
            (group == null || item.group == group) && run {
                val englishCategory = when (item.group) {
                    MetadataGroup.MAIN -> "general file dates"
                    MetadataGroup.CAMERA -> "camera lens exposure shutter"
                    MetadataGroup.LOCATION -> "gps location latitude longitude altitude"
                    MetadataGroup.EXIF -> "technical exif image software"
                    MetadataGroup.XMP -> "xmp iptc metadata"
                    MetadataGroup.PNG -> "png text comments"
                    MetadataGroup.VIDEO -> "video mp4 mov quicktime container tags"
                }
                val searchable = listOf(
                    englishCategory, item.group.marker, item.groupTitle, item.section,
                    item.label, item.value, item.key.orEmpty(),
                ).joinToString(" ").lowercase(Locale.ROOT)
                words.all { searchable.contains(it) }
            }
        }
    }
}
