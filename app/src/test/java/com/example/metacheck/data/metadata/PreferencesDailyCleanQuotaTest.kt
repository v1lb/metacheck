package com.example.metacheck.data.metadata

import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.testutil.FakePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.ProviderException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

/** Регрессия аудита, п. 5: сбой хранилища не должен навсегда блокировать бесплатный тариф. */
class PreferencesDailyCleanQuotaTest {

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant() = instant
    }

    /** Обратимое «шифрование» для тестов; можно сломать на лету. */
    private class SwitchableCrypto : Crypto {
        var broken = false
        override fun encrypt(plain: ByteArray): ByteArray {
            if (broken) throw ProviderException("keystore2 died")
            return plain.reversedArray()
        }
        override fun decrypt(blob: ByteArray): ByteArray {
            if (broken) throw ProviderException("keystore2 died")
            return blob.reversedArray()
        }
    }

    private val day1 = Instant.parse("2026-11-02T10:00:00Z")
    private val day2 = Instant.parse("2026-11-03T00:00:01Z")

    @Test fun countsAndResetsAtUtcMidnight() {
        val clock = MutableClock(day1)
        val q = PreferencesDailyCleanQuota(FakePrefs(), SwitchableCrypto(), clock)
        repeat(3) { assertTrue(q.recordSuccess()) }
        assertEquals(3, q.usedToday())
        clock.instant = day2
        assertEquals(0, q.usedToday())
    }

    @Test fun persistsAcrossInstances() {
        val prefs = FakePrefs(); val crypto = SwitchableCrypto(); val clock = MutableClock(day1)
        repeat(5) { PreferencesDailyCleanQuota(prefs, crypto, clock).recordSuccess() }
        assertEquals(5, PreferencesDailyCleanQuota(prefs, crypto, clock).usedToday())
    }

    @Test fun corruptedRecord_isDiscarded_notPermanentLockout() {
        val prefs = FakePrefs().apply { edit().putString("encrypted_record", "@@@not-base64@@@").apply() }
        val q = PreferencesDailyCleanQuota(prefs, SwitchableCrypto(), MutableClock(day1))
        assertEquals(0, q.usedToday())                // раньше: 75, навсегда
        assertTrue(q.recordSuccess())
        assertEquals(1, q.usedToday())
    }

    @Test fun recordFromOldKey_isDiscarded() {
        // Запись, зашифрованная потерянным ключом Keystore: расшифровка падает.
        val prefs = FakePrefs().apply {
            edit().putString("encrypted_record", Base64.getEncoder().encodeToString(ByteArray(40) { 7 })).apply()
        }
        val q = PreferencesDailyCleanQuota(prefs, SwitchableCrypto(), MutableClock(day1))
        assertEquals(0, q.usedToday())
    }

    @Test fun brokenKeystore_countsInMemory_andNeverLocksNextDay() {
        val crypto = SwitchableCrypto()
        val clock = MutableClock(day1)
        val q = PreferencesDailyCleanQuota(FakePrefs(), crypto, clock)
        repeat(2) { q.recordSuccess() }
        crypto.broken = true

        assertFalse(q.recordSuccess())                // на диск не записать
        assertEquals(3, q.usedToday())                // но лимит в сессии соблюдается

        clock.instant = day2
        assertEquals(0, q.usedToday())                // следующий день — новый счёт
    }

    @Test fun clockMovedBack_doesNotBlock() {
        val prefs = FakePrefs(); val crypto = SwitchableCrypto()
        val future = MutableClock(Instant.parse("2027-01-01T10:00:00Z"))
        repeat(75) { PreferencesDailyCleanQuota(prefs, crypto, future).recordSuccess() }

        val now = PreferencesDailyCleanQuota(prefs, crypto, MutableClock(day1))
        assertEquals(0, now.usedToday())
    }

    @Test fun countIsCappedAtLimit() {
        val q = PreferencesDailyCleanQuota(FakePrefs(), SwitchableCrypto(), MutableClock(day1))
        repeat(80) { q.recordSuccess() }
        assertEquals(75, q.usedToday())
    }

    @Test fun legacyRecordFormatIsReadable() {
        // Формат v2.6: base64(encrypt("<epochDay>:<count>")), android.util.Base64.NO_WRAP == java Base64.
        val crypto = SwitchableCrypto()
        val legacy = Base64.getEncoder().encodeToString(crypto.encrypt("${20394}:12".toByteArray()))
        val prefs = FakePrefs().apply { edit().putString("encrypted_record", legacy).apply() }
        val q = PreferencesDailyCleanQuota(prefs, crypto, MutableClock(Instant.parse("2025-11-02T10:00:00Z")))
        assertEquals(12, q.usedToday())
    }
}
