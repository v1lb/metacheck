package com.example.metacheck.domain.analytics

import com.example.metacheck.domain.model.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Derived exclusively from reports stored on device, never from fabricated AI confidence scores. */
data class HistoryInsights(
    val total: Int,
    val aiEvidenceCount: Int,
    val gpsCount: Int,
    val lastSevenDays: List<Int>,
) {
    companion object {
        fun from(entries: List<HistoryEntry>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): HistoryInsights {
            val bins = IntArray(7)
            entries.forEach { entry ->
                val day = Instant.ofEpochMilli(entry.createdAt).atZone(zone).toLocalDate()
                val daysAgo = today.toEpochDay() - day.toEpochDay()
                if (daysAgo in 0..6) bins[6 - daysAgo.toInt()]++
            }
            return HistoryInsights(
                total = entries.size,
                aiEvidenceCount = entries.count { it.hasAiEvidence },
                gpsCount = entries.count { it.hasGps },
                lastSevenDays = bins.toList(),
            )
        }
    }
}
