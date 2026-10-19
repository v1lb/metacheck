package com.example.metacheck.domain.metadata

import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import java.util.Locale

enum class MetadataGroup(val title: String, val marker: String) {
    MAIN("Основное", "FILE"),
    CAMERA("Камера", "CAM"),
    LOCATION("Геолокация", "GPS"),
    EXIF("Технические EXIF", "EXIF"),
    XMP("XMP / IPTC", "XMP"),
    PNG("PNG / текст", "PNG"),
}

data class MetadataRecord(
    val group: MetadataGroup,
    val section: String,
    val label: String,
    val value: String,
)

/** Index of all metadata fields that the current on-device parsers have extracted.
 * Neither infers nonexistent tags nor invents a confidence score. */
object MetadataCatalog {
    fun from(report: MetaReport): List<MetadataRecord> = buildList {
        fun addGroup(group: MetadataGroup, section: String, entries: List<MetaItem>) {
            entries.forEach { add(MetadataRecord(group, section, it.label, it.value)) }
        }
        val file = report.file
        addGroup(MetadataGroup.MAIN, "Файл", listOf(
            MetaItem("Имя", file.name),
            MetaItem("Формат", file.format.label),
            MetaItem("Тип MIME", file.mimeType),
            MetaItem("Размер, байт", file.sizeBytes.toString()),
            MetaItem("Прочитано, байт", file.scannedBytes.toString()),
            MetaItem("Частичное чтение", if (file.truncated) "Да" else "Нет"),
        ))
        for (section in report.exifSections) {
            val group = when (section.title) {
                "Камера и объектив", "Параметры съёмки" -> MetadataGroup.CAMERA
                "Даты" -> MetadataGroup.MAIN
                "Геолокация EXIF" -> MetadataGroup.LOCATION
                else -> MetadataGroup.EXIF
            }
            addGroup(group, section.title, section.items)
        }
        report.gps?.let { gps ->
            addGroup(MetadataGroup.LOCATION, "Координаты", buildList {
                add(MetaItem("Широта", gps.latitude.toString()))
                add(MetaItem("Долгота", gps.longitude.toString()))
                gps.altitudeMeters?.let { add(MetaItem("Высота, м", it.toString())) }
            })
        }
        addGroup(MetadataGroup.XMP, "XMP / IPTC", report.xmpItems)
        addGroup(MetadataGroup.PNG, "Текстовые блоки PNG", report.pngTextItems)
    }

    /** Case-insensitive and Unicode-aware search across tag name, value, section and category. */
    fun filter(items: List<MetadataRecord>, group: MetadataGroup?, query: String): List<MetadataRecord> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return items.filter { item ->
            (group == null || item.group == group) && run {
                val internationalCategory = when (item.group) {
                    MetadataGroup.MAIN -> "general file dates"
                    MetadataGroup.CAMERA -> "camera lens exposure shutter"
                    MetadataGroup.LOCATION -> "gps location latitude longitude altitude"
                    MetadataGroup.EXIF -> "technical exif image software"
                    MetadataGroup.XMP -> "xmp iptc metadata"
                    MetadataGroup.PNG -> "png text comments"
                }
                val englishField = when (item.label) {
                    "Имя" -> "name"; "Формат" -> "format"; "Размер, байт" -> "size bytes"
                    "Производитель" -> "make manufacturer"; "Модель" -> "model"
                    "Дата съёмки" -> "date taken"; "Дата изменения" -> "date modified"
                    "Выдержка" -> "shutter speed"; "Диафрагма" -> "aperture"
                    "Фокусное расстояние" -> "focal length"; "Баланс белого" -> "white balance"
                    "Широта" -> "latitude"; "Долгота" -> "longitude"
                    "Программа" -> "software"; "Автор" -> "author"; "Комментарий" -> "comment"
                    "Описание" -> "description"; "Высота" -> "altitude"; "Ширина" -> "width"
                    else -> ""
                }
                val searchable = "$internationalCategory $englishField ${item.group.title} ${item.section} ${item.label} ${item.value}".lowercase(Locale.ROOT)
                words.all { searchable.contains(it) }
            }
        }
    }
}
