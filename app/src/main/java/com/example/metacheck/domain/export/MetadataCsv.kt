package com.example.metacheck.domain.export

import com.example.metacheck.domain.metadata.MetadataRecord

object MetadataCsv {
    fun encode(records: List<MetadataRecord>): ByteArray {
        val text = buildString {
            append("category,section,tag,value\r\n")
            for (record in records) {
                append(listOf(record.group.title, record.section, record.label, record.value)
                    .joinToString(",", transform = ::escape))
                append("\r\n")
            }
        }
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
    }

    private fun escape(raw: String): String {
        val first = raw.trimStart().firstOrNull()
        val safe = if (first in setOf('=', '+', '-', '@') || raw.startsWith('\t') || raw.startsWith('\r'))
            "'" + raw else raw
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
