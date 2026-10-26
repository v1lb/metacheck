package com.example.metacheck.domain.repository

import android.app.Activity
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseInfo
import kotlinx.coroutines.flow.StateFlow

interface EntitlementRepository {
    val entitlement: StateFlow<Entitlement>

    /** Сверяет покупки с Google Play. Без сети остаётся последнее известное состояние. */
    suspend fun refresh()
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
