package com.example.metacheck.domain.privacy

import com.example.metacheck.domain.metadata.GpsPrivacy
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.SignalKind
import java.util.Locale

/**
 * Что о человеке рассказывают метаданные фото, если отправить оригинал файла.
 * Вес — вклад в индекс риска (0–100): точное место важнее модели телефона.
 */
enum class ExposureKind(val weight: Int) {
    EXACT_LOCATION(45),
    LOCATION_TAGS(25),
    OWNER(20),
    DEVICE_SERIAL(15),
    AUTHOR(10),
    AI_PROMPT(10),
    CAPTURE_TIME(8),
    DESCRIPTION(6),
    DEVICE(5),
    EDIT_HISTORY(4),
    SOFTWARE(2),
}

enum class ExposureLevel { NONE, LOW, MEDIUM, HIGH }

/** [value] — что именно видно (координаты, имя, серийный номер); пусто, если показывать нечего. */
data class Exposure(val kind: ExposureKind, val value: String)

data class PrivacyExposure(val score: Int, val items: List<Exposure>) {
    val level: ExposureLevel
        get() = when {
            items.isEmpty() -> ExposureLevel.NONE
            score < 20 -> ExposureLevel.LOW
            score < 45 -> ExposureLevel.MEDIUM
            else -> ExposureLevel.HIGH
        }
}

/**
 * Индекс приватности по уже прочитанным метаданным. Только локальный расчёт, без сети.
 * Поля ищутся по стабильным ключам (exif:…, xmp:…), поэтому язык интерфейса не важен.
 */
object PrivacyExposures {

    private const val MAX_VALUE = 80
    /**
     * Блоки PNG, где генераторы хранят промпт. «Comment» сюда не входит: его пишут и обычные редакторы
     * («Created with GIMP»). JSON NovelAI в Comment распознаёт SignalEngine (GENERATION_PARAMS).
     */
    private val promptKeywords = setOf("parameters", "prompt", "workflow", "dream", "sd-metadata", "invokeai_metadata")

    fun of(report: MetaReport): PrivacyExposure {
        val fields = (report.exifSections.flatMap { it.items } + report.xmpItems)
            .mapNotNull { item -> item.key?.let { it to item } }
            .groupBy({ it.first }, { it.second })
        fun value(vararg keys: String): String? = keys.asSequence()
            .flatMap { fields[it].orEmpty().asSequence() }
            .map(MetaItem::value)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
        fun values(vararg keys: String): String? = keys
            .mapNotNull { value(it) }
            .distinct()
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }

        val found = ArrayList<Exposure>()
        fun add(kind: ExposureKind, raw: String?) {
            if (raw != null) found += Exposure(kind, raw.take(MAX_VALUE))
        }

        report.gps?.let { add(ExposureKind.EXACT_LOCATION, String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)) }
        if (report.gps == null && GpsPrivacy.hasLocationMarkers(report)) add(ExposureKind.LOCATION_TAGS, "")
        add(ExposureKind.OWNER, value("exif:CameraOwnerName"))
        add(ExposureKind.DEVICE_SERIAL, values("exif:BodySerialNumber", "exif:LensSerialNumber"))
        add(ExposureKind.AUTHOR, values("exif:Artist", "exif:Copyright", "xmp:dc:creator", "xmp:dc:rights"))
        val hasPrompt = report.pngTextItems.any { it.label.lowercase(Locale.ROOT) in promptKeywords && it.value.isNotBlank() } ||
            report.signals.any { it.kind == SignalKind.SD_PARAMETERS || it.kind == SignalKind.GENERATION_PARAMS || it.kind == SignalKind.COMFYUI_GRAPH }
        if (hasPrompt) add(ExposureKind.AI_PROMPT, "")
        add(ExposureKind.CAPTURE_TIME, value("exif:DateTimeOriginal", "exif:DateTime", "xmp:xmp:CreateDate"))
        add(ExposureKind.DESCRIPTION, values("exif:ImageDescription", "exif:UserComment", "xmp:dc:description", "xmp:dc:title"))
        val device = listOfNotNull(value("exif:Make", "xmp:tiff:Make"), value("exif:Model", "xmp:tiff:Model"))
            .fold(emptyList<String>()) { acc, part ->
                // «Apple iPhone 15» вместо «Apple Apple iPhone 15»: модель часто уже содержит производителя.
                if (acc.any { part.contains(it, ignoreCase = true) }) acc.drop(1) + part else acc + part
            }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }
        add(ExposureKind.DEVICE, device)
        add(ExposureKind.EDIT_HISTORY, value("xmp:history_agents"))
        add(ExposureKind.SOFTWARE, values("exif:Software", "xmp:xmp:CreatorTool"))

        val items = found.sortedByDescending { it.kind.weight }
        return PrivacyExposure(items.sumOf { it.kind.weight }.coerceAtMost(100), items)
    }
}
