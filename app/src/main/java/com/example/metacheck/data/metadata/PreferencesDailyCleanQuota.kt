package com.example.metacheck.data.metadata

import android.content.Context
import android.util.Base64
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.domain.metadata.DailyCleanQuota
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** AES-256-GCM authenticated quota record, backed by Android Keystore.
 * Device-side protection is not a substitute for server-authoritative quotas.
 */
class PreferencesDailyCleanQuota(
    context: Context,
    private val crypto: Crypto,
    private val clock: Clock = Clock.systemUTC(),
) : DailyCleanQuota {
    private val prefs = context.applicationContext.getSharedPreferences("quota_secure_v2", Context.MODE_PRIVATE)
    private data class Record(val day: Long, val count: Int)
    private fun utcDay(): Long = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toEpochDay()

    private fun record(): Record? {
        val encoded = prefs.getString("encrypted_record", null) ?: return null
        return try {
            val plain = crypto.decrypt(Base64.decode(encoded, Base64.NO_WRAP)).toString(Charsets.UTF_8)
            val parts = plain.split(':')
            if (parts.size != 2) null else Record(parts[0].toLong(), parts[1].toInt().coerceIn(0, 75))
        } catch (_: Exception) {
            // Fail closed on corrupted or incompatible ciphertext.
            Record(utcDay(), 75)
        }
    }

    private fun effectiveDay(previous: Record?): Long = maxOf(utcDay(), previous?.day ?: utcDay())

    @Synchronized override fun usedToday(): Int {
        val previous = record() ?: return 0
        val day = effectiveDay(previous)
        return if (previous.day == day) previous.count else 0
    }

    @Synchronized override fun recordSuccess(): Boolean = try {
        val previous = record()
        val day = effectiveDay(previous)
        val current = if (previous?.day == day) previous.count else 0
        if (current >= 75) false else {
            val blob = crypto.encrypt("$day:${current + 1}".toByteArray(Charsets.UTF_8))
            prefs.edit().putString("encrypted_record", Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
        }
    } catch (_: Exception) { false }
}
