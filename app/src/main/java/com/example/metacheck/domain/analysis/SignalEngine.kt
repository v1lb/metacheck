package com.example.metacheck.domain.analysis

import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.PngTextEntry
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import java.util.Locale

data class SignalInput(
    /** Поля, где пишется программа: EXIF Software, XMP CreatorTool, softwareAgent. */
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
                    out += Signal(
                        Severity.AI_EVIDENCE,
                        "Параметры Stable Diffusion",
                        "В PNG есть блок «parameters» с промптом и настройками генерации (формат AUTOMATIC1111 и совместимых).",
                    )
                (key == "prompt" && text.contains("\"class_type\"")) ||
                    (key == "workflow" && text.contains("\"nodes\"")) ->
                    out += Signal(
                        Severity.AI_EVIDENCE,
                        "Граф ComfyUI",
                        "В PNG сохранён граф генерации ComfyUI (блок «${entry.keyword}»).",
                    )
                key in invokeAiKeys ->
                    out += Signal(
                        Severity.AI_EVIDENCE,
                        "Метаданные InvokeAI",
                        "В PNG есть блок «${entry.keyword}», который пишет InvokeAI.",
                    )
            }
        }

        val pngTools = input.pngText
            .filter { it.keyword.equals("Software", ignoreCase = true) || it.keyword.equals("Source", ignoreCase = true) }
            .map { MetaItem("PNG ${it.keyword}", it.text) }
        for (field in input.toolFields + pngTools) {
            val marker = findMarker(field.value) ?: continue
            out += Signal(
                Severity.AI_EVIDENCE,
                "Указан ИИ-инструмент: $marker",
                "Поле «${field.label}»: ${field.value.take(120)}",
            )
        }

        // В свободном тексте слово может быть случайным («firefly» на фото светлячков), поэтому только предупреждение.
        for (field in input.freeTextFields) {
            val marker = findMarker(field.value) ?: continue
            out += Signal(
                Severity.CAUTION,
                "Упоминание «$marker» в тексте",
                "Поле «${field.label}»: ${field.value.take(120)}. Это может быть и совпадением.",
            )
        }

        val sourceType = input.xmpFields[KEY_DIGITAL_SOURCE_TYPE]
        if (sourceType != null && sourceType.lowercase(Locale.ROOT).contains("trainedalgorithmicmedia")) {
            out += Signal(
                Severity.AI_EVIDENCE,
                "Метка IPTC: создано ИИ",
                "DigitalSourceType: $sourceType",
            )
        }

        if (input.hasC2pa) {
            out += Signal(
                Severity.INFO,
                "Найдены Content Credentials (C2PA)",
                "Подпись пока не проверяется, это будет в одном из следующих этапов.",
            )
        }

        if (input.hasGps) {
            out += Signal(
                Severity.CAUTION,
                "В файле есть геолокация",
                "Если отправить этот файл без очистки метаданных, получатель увидит место съёмки.",
            )
        }

        if (!input.hasAnyMetadata) {
            out += Signal(
                Severity.CAUTION,
                "Метаданных нет",
                "Файл мог пройти через мессенджер, быть скриншотом или обработан. Это не доказывает ни подлинность, ни ИИ.",
            )
        }

        if (input.truncated) {
            out += Signal(
                Severity.INFO,
                "Файл проанализирован частично",
                "Файл большой, метаданные искались только в начале.",
            )
        }

        if (out.none { it.severity == Severity.AI_EVIDENCE }) {
            out += Signal(
                Severity.INFO,
                "Явных следов ИИ в метаданных нет",
                "Метаданные легко удалить или подделать, поэтому это не доказывает, что изображение настоящее.",
            )
        }

        return out.distinctBy { it.title }.sortedBy { it.severity.ordinal }
    }

    private fun findMarker(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return aiMarkers.firstOrNull { lower.contains(it) }
    }
}
