package com.example.metacheck.domain.export

import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.Severity
import java.util.Locale

/** Текстовое представление отчёта. Из него делаются и буфер обмена, и PDF. */
object ReportText {
    enum class Kind { HEADING, LINE }

    data class Block(val kind: Kind, val text: String)

    private const val MAX_VALUE = 2000

    fun blocks(r: MetaReport): List<Block> {
        val out = mutableListOf<Block>()
        fun heading(t: String) { out += Block(Kind.HEADING, t) }
        fun line(t: String) { out += Block(Kind.LINE, t) }
        fun items(list: List<MetaItem>) {
            list.forEach { line("${it.label}: ${it.value.take(MAX_VALUE)}") }
        }

        heading("MetaCheck: отчёт об изображении")

        heading("Файл")
        val f = r.file
        line("Имя: ${f.name}")
        line("Формат: ${f.format.label}")
        line("MIME: ${f.mimeType}")
        line("Размер: ${if (f.sizeBytes >= 0) "${f.sizeBytes} байт" else "неизвестно"}")
        if (f.truncated) line("Проанализировано только начало файла (${f.scannedBytes} байт)")

        heading("ИИ-анализ")
        line(
            if (r.hasAiEvidence) "Найдены следы ИИ-генерации в метаданных"
            else "Явных следов ИИ в метаданных не найдено (это не доказывает подлинность)",
        )
        r.signals.forEach { s ->
            val tag = when (s.severity) {
                Severity.AI_EVIDENCE -> "Следы ИИ"
                Severity.CAUTION -> "Внимание"
                Severity.INFO -> "Заметка"
            }
            line("[$tag] ${s.title}: ${s.detail}")
        }

        r.exifSections.forEach { section ->
            heading(section.title)
            items(section.items)
        }
        if (r.xmpItems.isNotEmpty()) {
            heading("XMP / IPTC")
            items(r.xmpItems)
        }
        if (r.pngTextItems.isNotEmpty()) {
            heading("Текстовые блоки PNG")
            items(r.pngTextItems)
        }
        r.gps?.let { g ->
            heading("Геолокация")
            line(String.format(Locale.US, "Координаты: %.6f, %.6f", g.latitude, g.longitude))
            g.altitudeMeters?.let { line(String.format(Locale.US, "Высота: %.0f м", it)) }
        }
        return out
    }

    fun format(r: MetaReport): String =
        blocks(r).joinToString("\n") { if (it.kind == Kind.HEADING) "\n== ${it.text} ==" else it.text }.trim()
}
