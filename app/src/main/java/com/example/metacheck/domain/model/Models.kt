package com.example.metacheck.domain.model

/**
 * [label] — техническое имя формата, одинаковое во всех языках. Для [UNKNOWN] интерфейс
 * показывает локализованное «неизвестный формат» (см. ui/text/MetaTextsResources).
 */
enum class ImageFormat(val label: String) {
    JPEG("JPEG"),
    PNG("PNG"),
    WEBP("WebP"),
    GIF("GIF"),
    HEIC("HEIC/HEIF"),
    AVIF("AVIF"),
    UNKNOWN("unknown"),
}

/**
 * Как интерпретировать [MetaItem.value]. Если формат задан, в value лежит машинное значение
 * (число, код), а единицы и слова подставляет интерфейс на языке пользователя.
 */
enum class ValueFormat {
    /** Секунды, Double: 0.004 → «1/250 с» / «1/250 s». */
    EXPOSURE_SECONDS,
    /** Диафрагменное число, Double: 1.8 → «f/1.8». */
    F_NUMBER,
    /** Миллиметры, Double. */
    MILLIMETERS,
    /** Код ориентации EXIF, Int (1, 3, 6, 8, ...). */
    ORIENTATION,
    /** "true" / "false". */
    BOOLEAN,
    /** Целое число байт. */
    BYTES,
    /** Метры, Double. */
    METERS,
    /** Блок повреждён или превышает лимит: value пустое. */
    BAD_DATA,
}

/**
 * Поле метаданных.
 * [key] — стабильный идентификатор поля (например, "exif:Make", "xmp:dc:title", "file:name"),
 * по нему интерфейс берёт локализованную подпись. [label] — запасная подпись: для полей без ключа
 * (ключевые слова PNG) и для отчётов из истории, сохранённых до появления ключей.
 */
data class MetaItem(
    val label: String,
    val value: String,
    val key: String? = null,
    val format: ValueFormat? = null,
)

/** Смысловой раздел EXIF. Заголовок раздела локализует интерфейс по ключу. */
enum class SectionKey { CAMERA, EXPOSURE, IMAGE, DATES, GPS, AUTHORSHIP }

data class MetaSection(val title: String, val items: List<MetaItem>, val key: SectionKey? = null)

/** Текстовый блок PNG (tEXt / zTXt / iTXt). */
data class PngTextEntry(val keyword: String, val text: String)

enum class Severity { AI_EVIDENCE, CAUTION, INFO }

/** Вид сигнала. Текст заголовка и пояснения берётся из ресурсов, [Signal.args] подставляются в него. */
enum class SignalKind {
    /** args: — */
    SD_PARAMETERS,
    /** args: ключевое слово PNG-блока */
    COMFYUI_GRAPH,
    /** args: ключевое слово PNG-блока */
    INVOKEAI_METADATA,
    /** args: маркер, имя поля, значение (до 120 символов) */
    AI_TOOL_FIELD,
    /** args: маркер, имя поля, значение (до 120 символов) */
    AI_MENTION_TEXT,
    /** args: значение DigitalSourceType */
    IPTC_AI_SOURCE,
    C2PA_PRESENT,
    HAS_GPS,
    NO_METADATA,
    PARTIAL_SCAN,
    NO_AI_TRACES,
}

/**
 * Сигнал анализа. Новые сигналы задаются через [kind] и [args] и локализуются при показе.
 * [title]/[detail] — готовый текст только у отчётов из истории, сохранённых до версии с ключами.
 */
data class Signal(
    val severity: Severity,
    val title: String = "",
    val detail: String = "",
    val kind: SignalKind? = null,
    val args: List<String> = emptyList(),
) {
    /** Для удаления дублей: один и тот же маркер в разных полях даёт один сигнал. */
    val dedupeKey: String
        get() = when (kind) {
            null -> "text:$title"
            SignalKind.AI_TOOL_FIELD, SignalKind.AI_MENTION_TEXT -> "${kind.name}:${args.firstOrNull()}"
            else -> kind.name
        }
}

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

class UnsupportedFormatException : Exception("Unsupported file format")
