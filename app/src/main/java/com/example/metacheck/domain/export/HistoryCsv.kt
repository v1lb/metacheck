package com.example.metacheck.domain.export

import com.example.metacheck.domain.model.HistoryEntry
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** RFC 4180 quoting, UTF-8 BOM for Excel, and spreadsheet formula injection protection. */
object HistoryCsv {
    fun encode(entries: List<HistoryEntry>): ByteArray {
        val lines = buildString {
            append("date_utc,filename,format,ai_metadata_evidence,gps_present\r\n")
            entries.forEach { item ->
                val fields = listOf(
                    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(item.createdAt)),
                    item.fileName, item.format.label,
                    item.hasAiEvidence.toString(), item.hasGps.toString(),
                )
                append(fields.joinToString(",") { csvField(it) }).append("\r\n")
            }
        }
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + lines.toByteArray(Charsets.UTF_8)
    }

    private fun csvField(value: String): String {
        // Excel and spreadsheet clients can evaluate leading formula symbols after import.
        val trimmed = value.trimStart()
        val safe = if (trimmed.startsWith('=') || trimmed.startsWith('+') ||
            trimmed.startsWith('-') || trimmed.startsWith('@') || trimmed.startsWith('\t') || trimmed.startsWith('\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
