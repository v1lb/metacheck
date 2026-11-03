package com.example.metacheck.domain.export

import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.text.MetaTexts
import com.example.metacheck.domain.text.TextKey

/**
 * Текстовое представление отчёта на языке пользователя. Из него делаются и буфер обмена, и PDF.
 * Все фразы приходят из [MetaTexts], значения метаданных выводятся как есть.
 */
object ReportText {
    enum class Kind { HEADING, LINE }

    data class Block(val kind: Kind, val text: String)

    private const val MAX_VALUE = 2000

    fun blocks(r: MetaReport, texts: MetaTexts): List<Block> {
        val out = mutableListOf<Block>()
        fun heading(t: String) { out += Block(Kind.HEADING, t) }
        fun line(t: String) { out += Block(Kind.LINE, t) }
        fun items(list: List<MetaItem>) {
            list.forEach { line("${texts.itemLabel(it)}: ${texts.itemValue(it).take(MAX_VALUE)}") }
        }

        heading(texts.text(TextKey.REPORT_TITLE))

        heading(texts.text(TextKey.FILE))
        val f = r.file
        line(texts.text(TextKey.FILE_NAME_LINE, f.name.ifBlank { texts.text(TextKey.UNNAMED) }))
        line(texts.text(TextKey.FILE_FORMAT_LINE, texts.formatName(f.format)))
        line(texts.text(TextKey.FILE_MIME_LINE, f.mimeType.ifBlank { texts.text(TextKey.UNKNOWN) }))
        line(if (f.sizeBytes >= 0) texts.text(TextKey.FILE_SIZE_LINE, f.sizeBytes) else texts.text(TextKey.FILE_SIZE_UNKNOWN_LINE))
        if (f.truncated) line(texts.text(TextKey.FILE_PARTIAL_LINE, f.scannedBytes))

        heading(texts.text(TextKey.AI_ANALYSIS))
        line(texts.text(if (r.hasAiEvidence) TextKey.AI_FOUND else TextKey.AI_NOT_FOUND))
        r.signals.forEach { s ->
            line("[${texts.severityTag(s.severity)}] ${texts.signalTitle(s)}: ${texts.signalDetail(s)}")
        }

        r.exifSections.forEach { section ->
            heading(texts.sectionTitle(section))
            items(section.items)
        }
        if (r.xmpItems.isNotEmpty()) {
            heading(texts.text(TextKey.XMP_IPTC))
            items(r.xmpItems)
        }
        if (r.pngTextItems.isNotEmpty()) {
            heading(texts.text(TextKey.PNG_TEXT))
            items(r.pngTextItems)
        }
        r.gps?.let { g ->
            heading(texts.text(TextKey.GEOLOCATION))
            line(texts.text(TextKey.COORDINATES_LINE, g.latitude, g.longitude))
            g.altitudeMeters?.let { line(texts.text(TextKey.ALTITUDE_LINE, it)) }
        }
        return out
    }

    fun format(r: MetaReport, texts: MetaTexts): String =
        blocks(r, texts).joinToString("\n") { if (it.kind == Kind.HEADING) "\n== ${it.text} ==" else it.text }.trim()
}
