package com.example.metacheck.domain.analysis

import com.example.metacheck.domain.model.C2paFacts
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
    /** Сведения из Content Credentials; null — C2PA в файле нет. */
    val c2pa: C2paFacts? = null,
    val hasGps: Boolean = false,
    val hasAnyMetadata: Boolean = false,
    val truncated: Boolean = false,
    /** Имя файла: по нему видно копию из мессенджера или скриншот (там метаданных нет). */
    val fileName: String = "",
)

/**
 * Правила поиска следов ИИ в метаданных. Чистый Kotlin, без Android, поэтому тестируется на JVM.
 * Отсутствие следов ничего не доказывает: метаданные легко удалить.
 *
 * Источники по убыванию надёжности:
 *  1. C2PA и IPTC DigitalSourceType — метки, которые ставят сами генераторы (ChatGPT, Gemini, Firefly…).
 *  2. Параметры генерации (промпт, шаги, сэмплер) — их пишут локальные генераторы (A1111, ComfyUI…).
 *  3. Название генератора в полях программы (Software, CreatorTool).
 *  4. Упоминание генератора в свободном тексте — только предупреждение.
 */
object SignalEngine {

    const val KEY_DIGITAL_SOURCE_TYPE = "Iptc4xmpExt:DigitalSourceType"

    /** Второй аргумент C2PA_PRESENT: контейнер найден, но не прочитан. */
    const val C2PA_UNREADABLE = "unreadable"

    /**
     * Названия генераторов для полей программы. Каждое достаточно специфично, чтобы не совпасть
     * с обычной программой (поэтому «flux.1», а не «flux», и «microsoft designer», а не «designer»).
     */
    internal val aiMarkers = listOf(
        "midjourney", "dall-e", "dall·e", "dalle", "chatgpt", "gpt-4o", "gpt-image", "openai", "sora",
        "stable diffusion", "stablediffusion", "sdxl", "dreamstudio", "stability.ai", "stability ai",
        "comfyui", "automatic1111", "invokeai", "fooocus", "forge", "draw things", "diffusionbee",
        "novelai", "niji", "firefly", "leonardo.ai", "ideogram", "flux.1", "flux1", "black forest labs",
        "gemini", "imagen", "nano banana", "grok", "aurora", "bing image creator", "image creator from designer",
        "microsoft designer", "copilot", "meta ai", "imagine with meta", "emu", "kling", "hailuo", "minimax",
        "recraft", "playground ai", "playgroundai", "nightcafe", "krea", "seedream", "dreamina", "jimeng",
        "hunyuan", "qwen-image", "kandinsky", "shedevrum", "civitai", "tensor.art", "pixai", "craiyon",
        "starryai", "wombo", "runwayml", "artbreeder", "lexica", "mage.space", "getimg.ai", "tusiart",
    )

    /**
     * Короткие или многозначные маркеры, которые в свободном тексте (подпись, описание) слишком
     * часто встречаются в обычном смысле: «Gemini» — знак зодиака, «Aurora» — северное сияние.
     */
    private val ambiguousInText = setOf(
        "sora", "forge", "niji", "gemini", "imagen", "grok", "aurora", "copilot", "emu", "krea",
        "kling", "minimax", "lexica", "wombo", "jimeng",
    )

    /**
     * Маркеры, которые в поле программы встречаются у обычных приложений: Aurora — HDR-редактор,
     * Emu — эмуляторы, «imagen» — «изображение» по-испански, Gemini Photos — чистильщик галереи.
     * Для них нужен контекст: рядом должно быть слово, указывающее на генератор.
     */
    private val needsContextInTool = setOf("forge", "aurora", "emu", "copilot", "imagen", "gemini")
    private val aiContext = listOf(
        "diffusion", "webui", "xai", "google", "deepmind", "image generator", "imagine", "generat",
        " ai", "ai ", "(ai", "ai)",
    )

    private val invokeAiKeys = setOf("invokeai_metadata", "invokeai_graph", "sd-metadata")

    /** Текстовые параметры AUTOMATIC1111 и совместимых: «Steps: 20, Sampler: Euler a, Seed: 1». */
    private fun isA1111Parameters(text: String): Boolean =
        text.contains("Steps:") && (text.contains("Sampler:") || text.contains("Seed:") || text.contains("CFG scale:"))

    /** JSON с параметрами генерации (NovelAI «Comment», Fooocus, Draw Things и др.). */
    private fun isGenerationJson(text: String): Boolean {
        val t = text.trimStart()
        if (!t.startsWith("{")) return false
        val lower = t.lowercase(Locale.ROOT)
        val hasSteps = lower.contains("\"steps\"")
        val hasSampling = listOf("\"sampler\"", "\"seed\"", "\"cfg\"", "\"scale\"", "\"cfg_scale\"", "\"guidance\"")
            .any { lower.contains(it) }
        return hasSteps && hasSampling
    }

    fun evaluate(input: SignalInput): List<Signal> {
        val out = mutableListOf<Signal>()

        // 1. Content Credentials.
        val c2pa = input.c2pa
        // Для метки ИИ важнее программа самого действия (модель), чем того, кто записал манифест.
        val c2paGenerator = c2pa?.let { it.aiAgents.firstOrNull() ?: it.generator }.orEmpty()
        when {
            c2pa == null -> Unit
            c2pa.aiCreated -> out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.C2PA_AI_CREATED, args = listOf(c2paGenerator))
            c2pa.aiEdited -> out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.C2PA_AI_EDITED, args = listOf(c2paGenerator))
            else -> {
                out += Signal(Severity.INFO, kind = SignalKind.C2PA_PRESENT,
                    args = if (c2pa.parsed) listOf(c2pa.generator.orEmpty()) else listOf("", C2PA_UNREADABLE))
                // Манифест без метки ИИ, но записан генератором (например, старые версии ChatGPT).
                c2pa.generator?.let { gen ->
                    toolMarker(gen)?.let {
                        out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.AI_TOOL_FIELD,
                            args = listOf(it, "C2PA claim_generator", gen.take(120)))
                    }
                }
            }
        }

        // 2. IPTC DigitalSourceType в XMP.
        val sourceType = input.xmpFields[KEY_DIGITAL_SOURCE_TYPE]
        when (sourceType?.substringAfterLast('/')?.trim()?.lowercase(Locale.ROOT)) {
            C2paFacts.TRAINED.lowercase(Locale.ROOT) ->
                out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.IPTC_AI_SOURCE, args = listOf(sourceType))
            C2paFacts.COMPOSITE_TRAINED.lowercase(Locale.ROOT) ->
                out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.IPTC_AI_EDITED, args = listOf(sourceType))
        }

        // 3. Параметры генерации.
        for (entry in input.pngText) {
            val key = entry.keyword.lowercase(Locale.ROOT)
            val text = entry.text
            when {
                key == "parameters" && isA1111Parameters(text) ->
                    out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.SD_PARAMETERS)
                (key == "prompt" && text.contains("\"class_type\"")) ||
                    (key == "workflow" && text.contains("\"nodes\"")) ->
                    out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.COMFYUI_GRAPH, args = listOf(entry.keyword))
                key in invokeAiKeys ->
                    out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.INVOKEAI_METADATA, args = listOf(entry.keyword))
                isA1111Parameters(text) || isGenerationJson(text) ->
                    out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.GENERATION_PARAMS, args = listOf("PNG ${entry.keyword}"))
            }
        }
        // AUTOMATIC1111 и Forge пишут параметры в EXIF UserComment, когда сохраняют JPEG или WebP.
        for (field in input.freeTextFields) {
            if (isA1111Parameters(field.value) || isGenerationJson(field.value)) {
                out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.GENERATION_PARAMS, args = listOf(field.label))
            }
        }

        // 4. Название генератора в полях программы.
        val pngTools = input.pngText
            .filter { it.keyword.equals("Software", ignoreCase = true) || it.keyword.equals("Source", ignoreCase = true) }
            .map { MetaItem("PNG ${it.keyword}", it.text) }
        for (field in input.toolFields + pngTools) {
            val marker = toolMarker(field.value) ?: continue
            out += Signal(Severity.AI_EVIDENCE, kind = SignalKind.AI_TOOL_FIELD,
                args = listOf(marker, field.label, field.value.take(120)))
        }

        // 5. Свободный текст: слово может быть случайным («firefly» на фото светлячков), только предупреждение.
        for (field in input.freeTextFields) {
            val marker = textMarker(field.value) ?: continue
            out += Signal(Severity.CAUTION, kind = SignalKind.AI_MENTION_TEXT,
                args = listOf(marker, field.label, field.value.take(120)))
        }

        val evidence = out.any { it.severity == Severity.AI_EVIDENCE }
        // Объясняем, почему метаданных нет: чаще всего файл прошёл через мессенджер или это скриншот.
        if (!evidence) {
            copySource(input.fileName)?.let { source ->
                out += if (source == SCREENSHOT) Signal(Severity.CAUTION, kind = SignalKind.SCREENSHOT)
                else Signal(Severity.CAUTION, kind = SignalKind.MESSENGER_COPY, args = listOf(source))
            }
        }

        if (input.hasGps) out += Signal(Severity.CAUTION, kind = SignalKind.HAS_GPS)
        if (!input.hasAnyMetadata) out += Signal(Severity.CAUTION, kind = SignalKind.NO_METADATA)
        if (input.truncated) out += Signal(Severity.INFO, kind = SignalKind.PARTIAL_SCAN)

        if (!evidence) out += Signal(Severity.INFO, kind = SignalKind.NO_AI_TRACES)

        return out.distinctBy { it.dedupeKey }.sortedBy { it.severity.ordinal }
    }

    /** Маркер генератора в поле программы с проверкой контекста для многозначных слов. */
    internal fun toolMarker(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return aiMarkers.firstOrNull { marker ->
            containsWord(lower, marker) && (marker !in needsContextInTool || aiContext.any { lower.contains(it) })
        }
    }

    private fun textMarker(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return aiMarkers.firstOrNull { it !in ambiguousInText && containsWord(lower, it) }
    }

    /** Совпадение целым словом: «emu» не должно находиться в «emulator», «sora» в «sorauto». */
    private fun containsWord(text: String, marker: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(marker, from)
            if (at < 0) return false
            val before = text.getOrNull(at - 1)
            val after = text.getOrNull(at + marker.length)
            if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetter())) return true
            from = at + 1
        }
    }

    private const val SCREENSHOT = "screenshot"

    /** Шаблоны имён файлов, которые дают приложения при сохранении полученных фото. */
    private val copyPatterns = listOf(
        Regex("^IMG-\\d{8}-WA\\d+", RegexOption.IGNORE_CASE) to "WhatsApp",
        Regex("^WhatsApp Image \\d{4}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE) to "WhatsApp",
        Regex("^photo_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE) to "Telegram",
        Regex("^FB_IMG_\\d+", RegexOption.IGNORE_CASE) to "Facebook",
        Regex("^\\d+_\\d+_\\d+_[no]\\.(jpe?g|webp)$", RegexOption.IGNORE_CASE) to "Facebook / Instagram",
        Regex("^viber_image_\\d{4}", RegexOption.IGNORE_CASE) to "Viber",
        Regex("^signal-\\d{4}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE) to "Signal",
        Regex("^(Screenshot|Screen ?Shot|Screen_Recording|Capture d.écran|Schermata|Bildschirmfoto)", RegexOption.IGNORE_CASE) to SCREENSHOT,
    )

    internal fun copySource(fileName: String): String? =
        copyPatterns.firstOrNull { it.first.containsMatchIn(fileName.trim()) }?.second
}
