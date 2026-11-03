package com.example.metacheck.data.entitlement

import android.app.Activity
import android.content.SharedPreferences
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.data.license.LicenseVerifier
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseInfo
import com.example.metacheck.domain.model.PurchaseEvent
import com.example.metacheck.domain.repository.BillingGateway
import com.example.metacheck.domain.repository.EntitlementRepository
import com.example.metacheck.domain.runCatchingCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Pro = покупка в Google Play ИЛИ действующий лицензионный ключ.
 * Результат последней сверки с Play и ключ (в зашифрованном виде) кэшируются, поэтому Pro работает офлайн.
 * Ключ при каждом запуске проверяется заново, так что истёкший перестаёт действовать.
 *
 * Контракт устойчивости: [refresh] никогда не бросает исключений (кроме отмены корутины).
 * Сбой Keystore или Play приводит только к тому, что кэш не обновится, а состояние посчитается
 * по тому, что удалось прочитать. Раньше исключение из crypto.encrypt роняло процесс при каждом запуске.
 */
class EntitlementRepositoryImpl(
    private val prefs: SharedPreferences,
    private val crypto: Crypto,
    private val verifier: LicenseVerifier,
    private val billing: BillingGateway,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Куда сообщать о проглоченных ошибках (лог/крэш-репортер). По умолчанию ничего не делаем. */
    private val onError: (Throwable) -> Unit = {},
) : EntitlementRepository {

    private val scope = CoroutineScope(
        SupervisorJob() + io + CoroutineExceptionHandler { _, e -> onError(e) },
    )
    // Начальное значение Free, настоящее (Keystore + ECDSA) считается в фоне, а не в главном потоке:
    // репозиторий создаётся лениво при первом обращении из UI.
    private val state = MutableStateFlow(Entitlement())
    override val entitlement: StateFlow<Entitlement> = state.asStateFlow()
    private val initialLoad = CompletableDeferred<Unit>()

    /** Ожидающий оплаты платёж. Не кэшируется на диск: при каждом onResume перечитывается у Play. */
    @Volatile
    private var pending = false

    private val events = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    override val purchaseEvents: Flow<PurchaseEvent> = events.asSharedFlow()

    init {
        billing.onPurchaseUpdate = { event ->
            events.tryEmit(event)
            if (event.needsRefresh) scope.launch { refresh() }
        }
        scope.launch {
            try { state.value = computeNow() } finally { initialLoad.complete(Unit) }
        }
    }

    override suspend fun current(): Entitlement {
        initialLoad.await()
        return state.value
    }

    override suspend fun refresh() {
        withContext(io) {
            // Иначе первичная загрузка из кэша может перезаписать более свежий ответ Play.
            initialLoad.await()
            val snapshot = runCatchingCancellable { billing.queryPurchases() }
                .onFailure(onError)
                .getOrNull()
            if (snapshot != null) pending = snapshot.pending && !snapshot.owned
            val owned = snapshot?.owned
            val persisted = owned == null ||
                runCatchingCancellable { persistPlayOwned(owned) }.onFailure(onError).isSuccess
            state.value = if (persisted) {
                computeNow()
            } else {
                // Keystore сломан: кэш на диск не записан, но в этой сессии статус от Play верный.
                Entitlement.resolve(owned == true, storedLicense(), pending)
            }
        }
    }

    override suspend fun priceText(): String? =
        runCatchingCancellable { billing.priceText() }.onFailure(onError).getOrNull()

    override fun launchPurchase(activity: Activity): Boolean =
        try {
            billing.launchPurchase(activity)
        } catch (e: Exception) {
            onError(e)
            false
        }

    override suspend fun redeem(token: String): Result<LicenseInfo> = withContext(io) {
        runCatchingCancellable {
            val clean = token.trim()
            val info = verifier.verify(clean).getOrThrow()
            val blob = crypto.encrypt(clean.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(K_LICENSE, encode(blob)).apply()
            state.value = computeNow()
            info
        }
    }

    override fun removeLicense() {
        prefs.edit().remove(K_LICENSE).apply()
        scope.launch { state.value = computeNow() }
    }

    private fun persistPlayOwned(owned: Boolean) {
        val encrypted = crypto.encrypt(owned.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .remove("play_owned")
            .putString(K_PLAY, encode(encrypted))
            .apply()
    }

    private fun computeNow(): Entitlement =
        Entitlement.resolve(cachedPlayOwned(), storedLicense(), pending)

    private fun cachedPlayOwned(): Boolean =
        try {
            val blob = prefs.getString(K_PLAY, null) ?: return false
            crypto.decrypt(decode(blob)).toString(Charsets.UTF_8) == "true"
        } catch (e: Exception) {
            onError(e)
            false
        }

    private fun storedLicense(): LicenseInfo? =
        try {
            prefs.getString(K_LICENSE, null)?.let {
                val token = String(crypto.decrypt(decode(it)), Charsets.UTF_8)
                verifier.verify(token).getOrNull()
            }
        } catch (e: Exception) {
            onError(e)
            null
        }

    // java.util.Base64 (API 26+, minSdk = 26) даёт те же байты, что android.util.Base64.NO_WRAP,
    // но работает в JVM-тестах. Уже сохранённые значения читаются без миграции.
    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        const val K_PLAY = "play_owned_encrypted_v2"
        const val K_LICENSE = "license_enc"
    }
}
