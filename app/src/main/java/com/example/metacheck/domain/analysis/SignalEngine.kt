package com.example.metacheck.domain.analysis

import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.PngTextEntry
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.domain.model.SignalKind
import java.util.Locale

data class SignalInput(
    /**
     * Поля, где пишется программа: EXIF Software, XMP CreatorTool, softwareAgent.
     * [MetaItem.label] здесь — техническое имя поля (например, «EXIF Software»), оно попадает в текст
     * сигнала как есть и не переводится.
     */
    val toolFields: List<MetaItem> = emptyList(),
    /** Свободный текст: описание, комментарий, автор. */
    val freeTextFields: List<MetaItem> = emptyList(),
    val xmpFields: Map<String, String> = emptyMap(),
    val pngText: List<PngTextEntry> = emptyList(),
    val hasC2pa: Boolean = false,
    val hasGps: Boolean = false,
    val hasAnyMetadata: Boolean = false,
    val truncated: Boolean = false,
)

/**
 * Правила поиска следов ИИ в метаданных. Чистый Kotlin, без Android, поэтому тестируется на JVM.
 * Отсутствие следов ничего не доказывает: метаданные легко удалить.
 */
object SignalEngine {

    const val KEY_DIGITAL_SOURCE_TYPE = "Iptc4xmpExt:DigitalSourceType"

    private val aiMarkers = listOf(
        "midjourney",
        "dall-e",
        "dall·e",
        "stable diffusion",
        "comfyui",
        "automatic1111",
        "novelai",
        "firefly",
        "openai",
        "leonardo.ai",
    )

    private val invokeAiKeys = setOf("invokeai_metadata", "invokeai_graph", "sd-metadata")

    fun evaluate(input: SignalInput): List<Signal> {
        val out = mutableListOf<Signal>()

        for (entry in input.pngText) {
            val key = entry.keyword.lowercase(Locale.ROOT)
            val text = entry.text
            when {
                key == "parameters" && text.contains("Steps:") &&
                    (text.contains("Sampler:") || text.contains("Seed:")) ->
                    out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.SD_PARAMETERS)
                (key == "prompt" && text.contains("\"class_type\"")) ||
                    (key == "workflow" && text.contains("\"nodes\"")) ->
                    out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.COMFYUI_GRAPH, args = listOf(entry.keyword))
                key in invokeAiKeys ->
                    out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.INVOKEAI_METADATA, args = listOf(entry.keyword))
            }
        }

        val pngTools = input.pngText
            .filter { it.keyword.equals("Software", ignoreCase = true) || it.keyword.equals("Source", ignoreCase = true) }
            .map { MetaItem("PNG ${it.keyword}", it.text) }
        for (field in input.toolFields + pngTools) {
            val marker = findMarker(field.value) ?: continue
            out += Signal(
                Severity.AI_EVIDENCE,
                kind = SignalKind.AI_TOOL_FIELD,
                args = listOf(marker, field.label, field.value.take(120)),
            )
        }

        // В свободном тексте слово может быть случайным («firefly» на фото светлячков), поэтому только предупреждение.
        for (field in input.freeTextFields) {
            val marker = findMarker(field.value) ?: continue
            out += Signal(
                Severity.CAUTION,
                kind = SignalKind.AI_MENTION_TEXT,
                args = listOf(marker, field.label, field.value.take(120)),
            )
        }

        val sourceType = input.xmpFields[KEY_DIGITAL_SOURCE_TYPE]
        if (sourceType != null && sourceType.lowercase(Locale.ROOT).contains("trainedalgorithmicmedia")) {
            out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.IPTC_AI_SOURCE, args = listOf(sourceType))
        }

        if (input.hasC2pa) out += Signal(Severity.INFO, kind = SignalKind.C2PA_PRESENT)
        if (input.hasGps) out += Signal(Severity.CAUTION, kind = SignalKind.HAS_GPS)
        if (!input.hasAnyMetadata) out += Signal(Severity.CAUTION, kind = SignalKind.NO_METADATA)
        if (input.truncated) out += Signal(Severity.INFO, kind = SignalKind.PARTIAL_SCAN)

        if (out.none { it.severity == Severity.AI_EVIDENCE }) {
            out += Signal(Severity.INFO, kind = SignalKind.NO_AI_TRACES)
        }

        return out.distinctBy { it.dedupeKey }.sortedBy { it.severity.ordinal }
    }

    private fun findMarker(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return aiMarkers.firstOrNull { lower.contains(it) }
    }
}
