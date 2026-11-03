package com.example.metacheck.domain.repository

import android.app.Activity
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseInfo
import kotlinx.coroutines.flow.StateFlow

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
    suspend fun priceText(): String?
    fun launchPurchase(activity: Activity): Boolean
    suspend fun redeem(token: String): Result<LicenseInfo>
    fun removeLicense()
}

/** Тонкий слой над Google Play Billing, чтобы остальной код не зависел от библиотеки. */
interface BillingGateway {
    /** true/false: покупка есть/нет. null: Play недоступен, состояние неизвестно. */
    suspend fun queryOwned(): Boolean?
    suspend fun priceText(): String?
    fun launchPurchase(activity: Activity): Boolean
    var onPurchasesChanged: (() -> Unit)?
}
