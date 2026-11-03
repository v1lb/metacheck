package com.example.metacheck.domain.text

import com.example.metacheck.domain.metadata.MetadataGroup
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal

/**
 * Тексты для отчётов, CSV и каталога метаданных на языке пользователя.
 * Домен описывает только структуру; реализация в интерфейсе берёт строки из ресурсов Android
 * (ui/text/ResourceMetaTexts), в тестах — простая заглушка.
 */
interface MetaTexts {
    fun itemLabel(item: MetaItem): String
    fun itemValue(item: MetaItem): String
    fun sectionTitle(section: MetaSection): String
    fun groupTitle(group: MetadataGroup): String
    fun signalTitle(signal: Signal): String
    fun signalDetail(signal: Signal): String
    fun severityTag(severity: Severity): String
    fun formatName(format: ImageFormat): String
    fun text(key: TextKey, vararg args: Any): String
}

/** Фиксированные фразы отчёта и каталога. Аргументы указаны в комментариях. */
enum class TextKey {
    REPORT_TITLE,
    FILE,
    /** args: имя файла */
    FILE_NAME_LINE,
    /** args: формат */
    FILE_FORMAT_LINE,
    /** args: MIME */
    FILE_MIME_LINE,
    /** args: размер в байтах (Long) */
    FILE_SIZE_LINE,
    FILE_SIZE_UNKNOWN_LINE,
    /** args: прочитано байт (Int) */
    FILE_PARTIAL_LINE,
    AI_ANALYSIS,
    AI_FOUND,
    AI_NOT_FOUND,
    XMP_IPTC,
    PNG_TEXT,
    GEOLOCATION,
    /** args: широта, долгота (Double) */
    COORDINATES_LINE,
    /** args: высота в метрах (Double) */
    ALTITUDE_LINE,
    /** Последняя строка обрезанного PDF. args: предел страниц (Int) */
    PDF_TRUNCATED,

    // Каталог метаданных
    CATALOG_FILE_SECTION,
    CATALOG_COORDINATES_SECTION,
    FIELD_NAME,
    FIELD_FORMAT,
    FIELD_MIME,
    FIELD_SIZE_BYTES,
    FIELD_SCANNED_BYTES,
    FIELD_PARTIAL,
    FIELD_LATITUDE,
    FIELD_LONGITUDE,
    FIELD_ALTITUDE_M,
    YES,
    NO,
    UNNAMED,
    UNKNOWN,
}
