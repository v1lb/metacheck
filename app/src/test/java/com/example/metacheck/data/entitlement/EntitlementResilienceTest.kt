package com.example.metacheck.data.entitlement

import android.app.Activity
import android.content.SharedPreferences
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.license.LicenseVerifier
import com.example.metacheck.domain.model.ProSource
import com.example.metacheck.domain.repository.BillingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.ProviderException

/** Регрессия аудита, п. 1: сбой Keystore/Play не должен ронять приложение на старте. */
class EntitlementResilienceTest {

    @Test
    fun brokenKeystore_refreshDoesNotThrow_andPlayStatusStillApplies() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val repo = repo(crypto = BrokenCrypto(), billing = FakeBilling(owned = true), errors = errors)

        repo.refresh() // раньше: ProviderException -> крэш процесса

        assertEquals(ProSource.PLAY, repo.entitlement.value.source)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun brokenKeystore_constructionDoesNotThrow() {
        val prefs = FakePrefs().apply { edit().putString("play_owned_encrypted_v2", "AAAA").apply() }
        val repo = EntitlementRepositoryImpl(prefs, BrokenCrypto(), LicenseVerifier(""), FakeBilling(owned = null), Dispatchers.Unconfined)
        assertFalse(repo.entitlement.value.isPro)
    }

    @Test
    fun billingThrows_refreshDoesNotThrow_andKeepsCache() = runBlocking {
        val crypto = PlainCrypto()
        val prefs = FakePrefs()
        repo(crypto, FakeBilling(owned = true), prefs = prefs).refresh()

        val repo = repo(crypto, FakeBilling(throws = true), prefs = prefs)
        repo.refresh()

        assertEquals(ProSource.PLAY, repo.entitlement.value.source)
    }

    @Test
    fun purchaseCallbackWithBrokenKeystore_doesNotCrash() {
        val billing = FakeBilling(owned = true)
        val errors = mutableListOf<Throwable>()
        repo(BrokenCrypto(), billing, errors = errors)

        billing.onPurchasesChanged!!.invoke() // Unconfined: выполняется синхронно

        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun priceAndPurchase_swallowBillingErrors() = runBlocking {
        val repo = repo(PlainCrypto(), FakeBilling(throws = true))
        assertEquals(null, repo.priceText())
    }

    private fun repo(
        crypto: Crypto,
        billing: BillingGateway,
        prefs: SharedPreferences = FakePrefs(),
        errors: MutableList<Throwable> = mutableListOf(),
    ) = EntitlementRepositoryImpl(prefs, crypto, LicenseVerifier(""), billing, Dispatchers.Unconfined) { errors += it }
}

private class BrokenCrypto : Crypto {
    override fun encrypt(plain: ByteArray): ByteArray = throw ProviderException("keystore2 died")
    override fun decrypt(blob: ByteArray): ByteArray = throw ProviderException("keystore2 died")
}

private class PlainCrypto : Crypto {
    override fun encrypt(plain: ByteArray) = plain.copyOf()
    override fun decrypt(blob: ByteArray) = blob.copyOf()
}

private class FakeBilling(private val owned: Boolean? = null, private val throws: Boolean = false) : BillingGateway {
    override var onPurchasesChanged: (() -> Unit)? = null
    override suspend fun queryOwned(): Boolean? = if (throws) throw IllegalStateException("billing") else owned
    override suspend fun priceText(): String? = if (throws) throw IllegalStateException("billing") else "1 €"
    override fun launchPurchase(activity: Activity): Boolean = !throws
}

/** Минимальные SharedPreferences в памяти для JVM-тестов. */
private class FakePrefs : SharedPreferences {
    private val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = map[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String?) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clear = false
        override fun putString(k: String?, v: String?) = apply { pending[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { pending[k!!] = v }
        override fun putInt(k: String?, v: Int) = apply { pending[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { pending[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { pending[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { pending[k!!] = v }
        override fun remove(k: String?) = apply { removed += k!! }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) map.clear()
            removed.forEach { map.remove(it) }
            map.putAll(pending)
        }
    }
}
