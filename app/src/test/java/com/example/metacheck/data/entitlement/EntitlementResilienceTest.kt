package com.example.metacheck.data.entitlement

import android.app.Activity
import android.content.SharedPreferences
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.license.LicenseVerifier
import com.example.metacheck.domain.model.ProSource
import com.example.metacheck.domain.repository.BillingGateway
import com.example.metacheck.testutil.FakePrefs
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
