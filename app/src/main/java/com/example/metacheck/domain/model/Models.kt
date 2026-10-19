package com.example.metacheck.domain.model

enum class ImageFormat(val label: String) {
    JPEG("JPEG"),
    PNG("PNG"),
    WEBP("WebP"),
    GIF("GIF"),
    HEIC("HEIC/HEIF"),
    AVIF("AVIF"),
    UNKNOWN("Неизвестный"),
}

data class MetaItem(val label: String, val value: String)

data class MetaSection(val title: String, val items: List<MetaItem>)

/** Текстовый блок PNG (tEXt / zTXt / iTXt). */
data class PngTextEntry(val keyword: String, val text: String)

enum class Severity { AI_EVIDENCE, CAUTION, INFO }

data class Signal(val severity: Severity, val title: String, val detail: String)

data class GpsPoint(val latitude: Double, val longitude: Double, val altitudeMeters: Double?)

data class FileInfo(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val format: ImageFormat,
    val scannedBytes: Int,
    val truncated: Boolean,
)

data class MetaReport(
    val file: FileInfo,
    val exifSections: List<MetaSection>,
    val xmpItems: List<MetaItem>,
    val pngTextItems: List<MetaItem>,
    val gps: GpsPoint?,
    val signals: List<Signal>,
) {
    val hasAiEvidence: Boolean get() = signals.any { it.severity == Severity.AI_EVIDENCE }
    /** Informational notes are not detections and must not inflate the scan metric. */
    val actionableSignalCount: Int get() = signals.count { it.severity != Severity.INFO }
}

class UnsupportedFormatException : Exception("Формат файла не поддерживается")
