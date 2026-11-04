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
    /** Метка IPTC compositeWithTrainedAlgorithmicMedia: снимок дорисован или изменён ИИ. args: значение */
    IPTC_AI_EDITED,
    /** args: программа из манифеста (может быть пустой) */
    C2PA_PRESENT,
    /** Манифест C2PA: изображение создано ИИ (trainedAlgorithmicMedia). args: программа (может быть пустой) */
    C2PA_AI_CREATED,
    /** Манифест C2PA: в изображение вносили правки ИИ (composite…). args: программа (может быть пустой) */
    C2PA_AI_EDITED,
    /** Параметры генерации вне PNG-блока parameters (EXIF UserComment, JSON NovelAI/Fooocus). args: поле */
    GENERATION_PARAMS,
    /** Имя файла как у копии из мессенджера: метаданные там стёрты при отправке. args: приложение */
    MESSENGER_COPY,
    /** Имя файла как у скриншота: метаданных исходного изображения в нём нет. */
    SCREENSHOT,
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

/**
 * Что удалось прочитать из Content Credentials (C2PA). Подпись не проверяется: это сведения из
 * манифеста, а не криптографическое подтверждение.
 * [parsed] = false: контейнер найден, но разобрать его не удалось.
 * [generator]: программа, создавшая активный манифест (claim_generator), например «ChatGPT».
 * [sourceTypes]: значения digitalSourceType из действий всех манифестов (последняя часть адреса IPTC).
 * [aiAgents]: программы (softwareAgent) у действий с ИИ-источником.
 */
data class C2paFacts(
    val parsed: Boolean,
    val generator: String? = null,
    val sourceTypes: Set<String> = emptySet(),
    val aiAgents: List<String> = emptyList(),
) {
    val aiCreated: Boolean get() = sourceTypes.any { it.equals(TRAINED, ignoreCase = true) }
    val aiEdited: Boolean get() = sourceTypes.any { it.equals(COMPOSITE_TRAINED, ignoreCase = true) }

    companion object {
        const val TRAINED = "trainedAlgorithmicMedia"
        const val COMPOSITE_TRAINED = "compositeWithTrainedAlgorithmicMedia"
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
    /**
     * The AI-label verdict is inconclusive: the file is truncated, or a caution signal says the metadata
     * may be missing or mentions AI. GPS is a privacy warning, not an AI hint, so it does not count.
     */
    val needsAiReview: Boolean get() = !hasAiEvidence &&
        (file.truncated || signals.any { it.severity == Severity.CAUTION && it.kind != SignalKind.HAS_GPS })
}

class UnsupportedFormatException : Exception("Unsupported file format")
