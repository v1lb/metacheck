package com.example.metacheck.data.metadata

import android.content.Context
import com.example.metacheck.domain.metadata.DailyCleanQuota
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Stored in private app preferences. UTC days keep the rule stable across timezone changes. */
class PreferencesDailyCleanQuota(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : DailyCleanQuota {
    private val prefs = context.applicationContext.getSharedPreferences("metadata_clean_quota_v1", Context.MODE_PRIVATE)

    private fun today(): String = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString()

    @Synchronized override fun usedToday(): Int =
        if (prefs.getString("day", "") == today()) prefs.getInt("count", 0).coerceAtLeast(0) else 0

    @Synchronized override fun recordSuccess(): Boolean {
        val day = today()
        val count = if (prefs.getString("day", "") == day) prefs.getInt("count", 0) else 0
        return prefs.edit().putString("day", day).putInt("count", (count + 1).coerceAtMost(999)).commit()
    }
}
