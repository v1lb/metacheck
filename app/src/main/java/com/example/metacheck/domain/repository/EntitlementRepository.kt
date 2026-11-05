package com.example.metacheck.domain.repository

import android.app.Activity
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseInfo
import com.example.metacheck.domain.model.PurchaseEvent
import com.example.metacheck.domain.model.PurchaseSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

interface EntitlementRepository {
    val entitlement: StateFlow<Entitlement>

    /**
     * Сверяет покупки с Google Play. Без сети остаётся последнее известное состояние.
     * Никогда не бросает исключений (кроме CancellationException): вызывается на каждом запуске.
     */
    suspend fun refresh()

    /**
     * Актуальное состояние Pro для проверок доступа. В отличие от [entitlement].value, дожидается
     * первичной загрузки кэша (она идёт в фоне, чтобы не трогать Keystore в главном потоке).
     */
    suspend fun current(): Entitlement = entitlement.value

    /** События покупки для сообщений пользователю (оплачено, ожидает оплаты, отменено...). */
    val purchaseEvents: Flow<PurchaseEvent> get() = emptyFlow()
    suspend fun priceText(): String?
    fun launchPurchase(activity: Activity): Boolean
    suspend fun redeem(token: String): Result<LicenseInfo>
    fun removeLicense()

    /** Цена платежа «Поддержать разработчика»; null, если товар недоступен. */
    suspend fun tipPriceText(): String? = null
    fun launchTip(activity: Activity): Boolean = false
}

/** Тонкий слой над Google Play Billing, чтобы остальной код не зависел от библиотеки. */
interface BillingGateway {
    /** Состояние покупок. null: Play недоступен, состояние неизвестно. */
    suspend fun queryPurchases(): PurchaseSnapshot?
    suspend fun priceText(): String?
    fun launchPurchase(activity: Activity): Boolean
    suspend fun tipPriceText(): String? = null
    fun launchTip(activity: Activity): Boolean = false

    /** Результат окна покупки и обновления покупок от Play. Вызывается в главном потоке. */
    var onPurchaseUpdate: ((PurchaseEvent) -> Unit)?
}
