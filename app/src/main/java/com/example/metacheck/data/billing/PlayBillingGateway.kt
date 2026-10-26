package com.example.metacheck.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.metacheck.domain.repository.BillingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Разовая покупка Pro через Google Play Billing Library 8.
 * Идентификатор товара в Play Console должен совпадать с [PRODUCT_ID].
 * Вызовы выполняются на главном потоке, как рекомендует библиотека.
 */
class PlayBillingGateway(context: Context) : BillingGateway {

    override var onPurchasesChanged: (() -> Unit)? = null

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(
            PurchasesUpdatedListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { acknowledgeIfNeeded(it) }
                    onPurchasesChanged?.invoke()
                }
            },
        )
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override suspend fun queryOwned(): Boolean? = withContext(Dispatchers.Main.immediate) {
        if (!ensureConnected()) return@withContext null
        withTimeoutOrNull(BILLING_TIMEOUT_MS) { suspendCancellableCoroutine<Boolean?> { cont ->
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            client.queryPurchasesAsync(params) { result, purchases ->
                if (!cont.isActive) return@queryPurchasesAsync
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    cont.resume(null)
                } else {
                    val owned = purchases.filter { it.isProPurchased() }
                    owned.forEach { acknowledgeIfNeeded(it) }
                    cont.resume(owned.isNotEmpty())
                }
            }
        } }
    }

    override suspend fun priceText(): String? = withContext(Dispatchers.Main.immediate) {
        details?.let { return@withContext it.oneTimePurchaseOfferDetails?.formattedPrice }
        if (!ensureConnected()) return@withContext null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        withTimeoutOrNull(BILLING_TIMEOUT_MS) { suspendCancellableCoroutine<String?> { cont ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                if (!cont.isActive) return@queryProductDetailsAsync
                val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryResult.productDetailsList.firstOrNull()
                } else {
                    null
                }
                details = found
                cont.resume(found?.oneTimePurchaseOfferDetails?.formattedPrice)
            }
        } }
    }

    override fun launchPurchase(activity: Activity): Boolean {
        val product = details ?: return false
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
            )
            .build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun Purchase.isProPurchased(): Boolean =
        products.contains(PRODUCT_ID) && purchaseState == Purchase.PurchaseState.PURCHASED

    /** Без подтверждения в течение 3 дней Google автоматически вернёт деньги. */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isProPurchased() && !purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { }
        }
    }

    private suspend fun ensureConnected(): Boolean =
        if (client.isReady) {
            true
        } else {
            withTimeoutOrNull(BILLING_TIMEOUT_MS) { suspendCancellableCoroutine<Boolean> { cont ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            // Подключимся заново при следующем обращении.
                        }
                    },
                )
            } } ?: false
        }

    companion object {
        private const val BILLING_TIMEOUT_MS = 15_000L
        /** Должен совпадать с идентификатором товара в Play Console. */
        const val PRODUCT_ID = "pro_lifetime"
    }
}
