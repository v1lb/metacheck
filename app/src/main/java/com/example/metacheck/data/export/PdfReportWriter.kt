package com.example.metacheck.data.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.metacheck.domain.export.ReportText
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.text.MetaTexts
import com.example.metacheck.domain.text.TextKey
import java.io.OutputStream

/** Простой постраничный PDF из текстового представления отчёта (формат A4) на языке пользователя. */
object PdfReportWriter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val MAX_PAGES = 40

    fun write(report: MetaReport, out: OutputStream, texts: MetaTexts) {
        val normal = Paint().apply { textSize = 10.5f; isAntiAlias = true; color = Color.BLACK }
        val bold = Paint(normal).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 13f }
        val width = PAGE_W - 2 * MARGIN

        val doc = PdfDocument()
        try {
            var pageNo = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            var y = MARGIN

            // На последней разрешённой странице снизу оставляем место под пометку о сокращении.
            val notice = wrap(texts.text(TextKey.PDF_TRUNCATED, MAX_PAGES), bold, width)
            val reserve = notice.size * bold.fontSpacing + 8f
            fun bottom(): Float = PAGE_H - MARGIN - if (pageNo >= MAX_PAGES) reserve else 0f
            var truncated = false

            fun newPage(): Boolean {
                if (pageNo >= MAX_PAGES) return false
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                y = MARGIN
                return true
            }

            loop@ for (block in ReportText.blocks(report, texts)) {
                val heading = block.kind == ReportText.Kind.HEADING
                val paint = if (heading) bold else normal
                val lineHeight = paint.fontSpacing
                if (heading) y += 8f
                for (rawLine in block.text.split('\n')) {
                    for (line in wrap(rawLine, paint, width)) {
                        if (y + lineHeight > bottom() && !newPage()) {
                            truncated = true
                            break@loop
                        }
                        page.canvas.drawText(line, MARGIN, y + lineHeight - paint.descent(), paint)
                        y += lineHeight
                    }
                }
            }
            // Аудит, п. 29: раньше PDF молча обрывался на 40-й странице, и читатель не знал, что часть данных пропала.
            if (truncated) {
                y += 8f
                for (line in notice) {
                    page.canvas.drawText(line, MARGIN, y + bold.fontSpacing - bold.descent(), bold)
                    y += bold.fontSpacing
                }
            }
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, width, null)
            if (count <= 0) break
            var end = start + count
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end - 1)
                if (space > start) end = space + 1
            }
            lines += text.substring(start, end).trimEnd()
            start = end
        }
        return lines
    }
}
