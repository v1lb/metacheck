package com.example.metacheck

import com.example.metacheck.domain.analytics.HistoryInsights
import com.example.metacheck.domain.export.HistoryCsv
import com.example.metacheck.domain.model.HistoryEntry
import com.example.metacheck.domain.model.ImageFormat
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private fun entry(day: String, name: String, ai: Boolean = false, gps: Boolean = false) = HistoryEntry(
        id = "test-$name",
        createdAt = LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        fileName = name,
        format = ImageFormat.JPEG,
        hasAiEvidence = ai,
        hasGps = gps,
    )

    @Test fun aggregatesOnlyObservedHistory() {
        val now = LocalDate.parse("2026-10-19")
        val list = listOf(entry("2026-10-19", "photo", ai = true),
            entry("2026-10-17", "another", gps = true), entry("2026-10-10", "old"))
        val stats = HistoryInsights.from(list, now, ZoneOffset.UTC)
        assertEquals(3, stats.total)
        assertEquals(1, stats.aiEvidenceCount)
        assertEquals(1, stats.gpsCount)
        assertEquals(listOf(0, 0, 0, 0, 1, 0, 1), stats.lastSevenDays)
    }

    @Test fun csvProtectsAgainstFormulaExecutionAndEscapesQuotes() {
        val entry = entry("2026-10-19", "=HYPERLINK(\"https://evil.example\",\"x\")")
        val csv = String(HistoryCsv.encode(listOf(entry)), Charsets.UTF_8)
        assertTrue(csv.startsWith("\uFEFFdate_utc,filename"))
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://evil.example\"\",\"\"x\"\")\""))
        assertTrue(csv.contains("\"JPEG\",\"false\",\"false\""))
    }
}
