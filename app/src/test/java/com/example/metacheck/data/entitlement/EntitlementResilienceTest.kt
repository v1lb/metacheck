package com.example.metacheck.data.entitlement

import android.app.Activity
import android.content.SharedPreferences
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.license.LicenseVerifier
import com.example.metacheck.domain.model.ProSource
import com.example.metacheck.domain.model.PurchaseEvent
import com.example.metacheck.domain.model.PurchaseSnapshot
import com.example.metacheck.domain.repository.BillingGateway
import com.example.metacheck.testutil.FakePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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

        billing.onPurchaseUpdate!!.invoke(PurchaseEvent.COMPLETED) // Unconfined: выполняется синхронно

        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun priceAndPurchase_swallowBillingErrors() = runBlocking {
        val repo = repo(PlainCrypto(), FakeBilling(throws = true))
        assertEquals(null, repo.priceText())
    }

    // --- п. 8: отложенные платежи и события покупки ---

    @Test
    fun pendingPurchase_isVisible_butDoesNotGrantPro() = runBlocking {
        val repo = repo(PlainCrypto(), FakeBilling(owned = false, pending = true))
        repo.refresh()
        assertFalse(repo.entitlement.value.isPro)
        assertTrue(repo.entitlement.value.purchasePending)
    }

    @Test
    fun pendingThenPaid_onResumeRefresh_grantsPro() = runBlocking {
        val billing = FakeBilling(owned = false, pending = true)
        val repo = repo(PlainCrypto(), billing)
        repo.refresh()
        billing.owned = true; billing.pending = false // оплатили наличными вне приложения
        repo.refresh()                               // ON_RESUME
        assertEquals(ProSource.PLAY, repo.entitlement.value.source)
        assertFalse(repo.entitlement.value.purchasePending)
    }

    @Test
    fun refund_revokesPro() = runBlocking {
        val billing = FakeBilling(owned = true)
        val repo = repo(PlainCrypto(), billing)
        repo.refresh()
        billing.owned = false
        repo.refresh()
        assertFalse(repo.entitlement.value.isPro)
    }

    @Test
    fun purchaseEvents_reachUi_andCanceledDoesNotQueryPlay() = runBlocking {
        val billing = FakeBilling(owned = false)
        val repo = repo(PlainCrypto(), billing)
        val seen = mutableListOf<PurchaseEvent>()
        val job = launch(Dispatchers.Unconfined) { repo.purchaseEvents.toList(seen) }

        billing.onPurchaseUpdate!!.invoke(PurchaseEvent.CANCELED)
        assertEquals(0, billing.queries)
        billing.onPurchaseUpdate!!.invoke(PurchaseEvent.ALREADY_OWNED)
        assertEquals(1, billing.queries)

        assertEquals(listOf(PurchaseEvent.CANCELED, PurchaseEvent.ALREADY_OWNED), seen)
        job.cancel()
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

private class FakeBilling(
    var owned: Boolean? = null,
    private val throws: Boolean = false,
    var pending: Boolean = false,
) : BillingGateway {
    var queries = 0
    override var onPurchaseUpdate: ((PurchaseEvent) -> Unit)? = null
    override suspend fun queryPurchases(): PurchaseSnapshot? {
        queries++
        if (throws) throw IllegalStateException("billing")
        return owned?.let { PurchaseSnapshot(owned = it, pending = pending) }
    }
    override suspend fun priceText(): String? = if (throws) throw IllegalStateException("billing") else "1 €"
    override fun launchPurchase(activity: Activity): Boolean = !throws
}
