package com.example.metacheck.data.metadata

import android.content.Context
import android.content.SharedPreferences
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.domain.metadata.DailyCleanQuota
import com.example.metacheck.domain.metadata.DailyCleanQuota.Companion.FREE_DAILY_LIMIT
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

/**
 * Счётчик бесплатных обработок за UTC-сутки. Запись зашифрована AES-GCM (ключ в Android Keystore),
 * чтобы её нельзя было просто отредактировать в shared_prefs на рутованном телефоне.
 *
 * Политика ошибок: FAIL-OPEN.
 * Раньше любая ошибка расшифровки превращалась в «75 из 75 на сегодня», запись больше никогда не
 * перезаписывалась, и после сбоя Keystore бесплатный тариф блокировался навсегда. Защиты это не давало:
 * «Очистить данные» обнуляет счётчик в любом случае.
 *  - Повреждённая или нечитаемая запись удаляется, счёт начинается с нуля.
 *  - Если записать на диск не удалось, счёт ведётся в памяти до конца сессии.
 *  - Дата в записи из «будущего» (часы переводили назад) не блокирует: другой день = новый счёт.
 */
class PreferencesDailyCleanQuota(
    private val prefs: SharedPreferences,
    private val crypto: Crypto,
    private val clock: Clock = Clock.systemUTC(),
    private val onError: (Throwable) -> Unit = {},
) : DailyCleanQuota {

    constructor(context: Context, crypto: Crypto) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        crypto,
    )

    private data class Record(val day: Long, val count: Int)

    /** Запасной счётчик, если Keystore или диск недоступны. */
    private var memory: Record? = null

    private fun utcDay(): Long = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toEpochDay()

    private fun stored(): Record? {
        val encoded = prefs.getString(KEY, null) ?: return null
        return try {
            val plain = crypto.decrypt(Base64.getDecoder().decode(encoded)).toString(Charsets.UTF_8)
            val parts = plain.split(':')
            require(parts.size == 2) { "Bad quota record" }
            Record(parts[0].toLong(), parts[1].toInt().coerceIn(0, FREE_DAILY_LIMIT))
        } catch (e: Exception) {
            onError(e)
            prefs.edit().remove(KEY).apply()
            null
        }
    }

    private fun countFor(day: Long, record: Record?): Int = if (record?.day == day) record.count else 0

    @Synchronized
    override fun usedToday(): Int {
        val today = utcDay()
        return maxOf(countFor(today, stored()), countFor(today, memory))
    }

    @Synchronized
    override fun recordSuccess(): Boolean {
        val today = utcDay()
        val next = Record(today, (usedToday() + 1).coerceAtMost(FREE_DAILY_LIMIT))
        memory = next
        return try {
            val blob = crypto.encrypt("${next.day}:${next.count}".toByteArray(Charsets.UTF_8))
            prefs.edit().putString(KEY, Base64.getEncoder().encodeToString(blob)).apply()
            true
        } catch (e: Exception) {
            onError(e)
            false
        }
    }

    private companion object {
        const val PREFS_NAME = "quota_secure_v2"
        const val KEY = "encrypted_record"
    }
}
