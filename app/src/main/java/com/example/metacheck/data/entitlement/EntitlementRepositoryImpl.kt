package com.example.metacheck.data.entitlement

import android.app.Activity
import android.content.SharedPreferences
import android.util.Base64
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.license.LicenseVerifier
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseInfo
import com.example.metacheck.domain.repository.BillingGateway
import com.example.metacheck.domain.repository.EntitlementRepository
import com.example.metacheck.domain.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pro = покупка в Google Play ИЛИ действующий лицензионный ключ.
 * Результат последней сверки с Play и ключ (в зашифрованном виде) кэшируются, поэтому Pro работает офлайн.
 * Ключ при каждом запуске проверяется заново, так что истёкший перестаёт действовать.
 */
class EntitlementRepositoryImpl(
    private val prefs: SharedPreferences,
    private val crypto: Crypto,
    private val verifier: LicenseVerifier,
    private val billing: BillingGateway,
) : EntitlementRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow(computeNow())
    override val entitlement: StateFlow<Entitlement> = state.asStateFlow()

    init {
        billing.onPurchasesChanged = { scope.launch { refresh() } }
    }

    override suspend fun refresh() {
        billing.queryOwned()?.let { prefs.edit().putBoolean(K_PLAY, it).apply() }
        state.value = computeNow()
    }

    override suspend fun priceText(): String? = billing.priceText()

    override fun launchPurchase(activity: Activity): Boolean = billing.launchPurchase(activity)

    override suspend fun redeem(token: String): Result<LicenseInfo> = withContext(Dispatchers.Default) {
        runCatchingCancellable {
            val clean = token.trim()
            val info = verifier.verify(clean).getOrThrow()
            val blob = crypto.encrypt(clean.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(K_LICENSE, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
            state.value = computeNow()
            info
        }
    }

    override fun removeLicense() {
        prefs.edit().remove(K_LICENSE).apply()
        state.value = computeNow()
    }

    private fun computeNow(): Entitlement =
        Entitlement.resolve(prefs.getBoolean(K_PLAY, false), storedLicense())

    private fun storedLicense(): LicenseInfo? =
        try {
            prefs.getString(K_LICENSE, null)?.let {
                val token = String(crypto.decrypt(Base64.decode(it, Base64.NO_WRAP)), Charsets.UTF_8)
                verifier.verify(token).getOrNull()
            }
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val K_PLAY = "play_owned"
        const val K_LICENSE = "license_enc"
    }
}
