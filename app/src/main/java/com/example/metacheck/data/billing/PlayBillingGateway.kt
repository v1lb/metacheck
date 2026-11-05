package com.example.metacheck.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.metacheck.domain.model.PurchaseEvent
import com.example.metacheck.domain.model.PurchaseSnapshot
import com.example.metacheck.domain.repository.BillingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Разовая покупка Pro через Google Play Billing Library 8.
 * Идентификатор товара в Play Console должен совпадать с [PRODUCT_ID].
 * Вызовы выполняются на главном потоке, как рекомендует библиотека.
 *
 * Исправлено (аудит, п. 8):
 *  - подключение защищено Mutex: параллельные refresh() и priceText() больше не вызывают
 *    startConnection дважды (второй вызов получал ошибку, и кнопка «Купить» оставалась неактивной);
 *  - enableAutoServiceReconnection(): библиотека сама переподключается после разрыва связи с Play;
 *  - результат покупки разбирается полностью: PENDING, ITEM_ALREADY_OWNED, отмена, ошибка.
 */
class PlayBillingGateway(
    context: Context,
    /** Подпись покупок (аудит, п. 26): ключ из Play Console, см. [PlayPublicKey]. */
    private val signature: PurchaseSignature = PurchaseSignature(PlayPublicKey.BASE64),
) : BillingGateway {

    override var onPurchaseUpdate: ((PurchaseEvent) -> Unit)? = null

    @Volatile
    private var details: ProductDetails? = null
    @Volatile
    private var tipDetails: ProductDetails? = null
    /** Какое окно покупки открыто последним: ответ ITEM_ALREADY_OWNED не говорит, о каком товаре речь. */
    @Volatile
    private var lastLaunchWasTip = false
    private val connectLock = Mutex()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(PurchasesUpdatedListener { result, purchases -> onPurchasesUpdated(result, purchases) })
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val event = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val tips = purchases.orEmpty().filter { it.products.contains(TIP_PRODUCT_ID) && isGenuine(it) }
                tips.forEach(::consumeIfPurchased)
                val all = purchases.orEmpty().filter { it.products.contains(PRODUCT_ID) }
                val ours = all.filter(::isGenuine)
                ours.forEach { acknowledgeIfNeeded(it) }
                when {
                    // Есть «покупка», но подпись не сходится: подделка, Pro не включаем.
                    ours.isEmpty() && all.isNotEmpty() -> PurchaseEvent.FAILED
                    ours.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> PurchaseEvent.COMPLETED
                    ours.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> PurchaseEvent.PENDING
                    tips.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> PurchaseEvent.TIP_COMPLETED
                    else -> PurchaseEvent.UPDATED
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseEvent.CANCELED
            // Неизрасходованный платёж поддержки: расходуем при следующей проверке, Pro тут ни при чём.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                if (lastLaunchWasTip) PurchaseEvent.FAILED else PurchaseEvent.ALREADY_OWNED
            else -> PurchaseEvent.FAILED
        }
        onPurchaseUpdate?.invoke(event)
    }

    override suspend fun queryPurchases(): PurchaseSnapshot? = withContext(Dispatchers.Main.immediate) {
        if (!ensureConnected()) return@withContext null
        withTimeoutOrNull(BILLING_TIMEOUT_MS) {
            suspendCancellableCoroutine<PurchaseSnapshot?> { cont ->
                val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
                client.queryPurchasesAsync(params) { result, purchases ->
                    if (!cont.isActive) return@queryPurchasesAsync
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        cont.resume(null)
                    } else {
                        // Платёж поддержки, который не удалось израсходовать раньше, иначе его нельзя купить снова.
                        purchases.filter { it.products.contains(TIP_PRODUCT_ID) && isGenuine(it) }.forEach(::consumeIfPurchased)
                        // Покупки с неверной подписью не считаются: ни Pro, ни подтверждения.
                        val ours = purchases.filter { it.products.contains(PRODUCT_ID) && isGenuine(it) }
                        // Повторное подтверждение при каждой проверке: без него через 3 дня Google вернёт деньги.
                        ours.forEach { acknowledgeIfNeeded(it) }
                        cont.resume(
                            PurchaseSnapshot(
                                owned = ours.any { it.purchaseState == Purchase.PurchaseState.PURCHASED },
                                pending = ours.any { it.purchaseState == Purchase.PurchaseState.PENDING },
                            ),
                        )
                    }
                }
            }
        }
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
        withTimeoutOrNull(BILLING_TIMEOUT_MS) {
            suspendCancellableCoroutine<String?> { cont ->
                client.queryProductDetailsAsync(params) { result, queryResult ->
                    if (!cont.isActive) return@queryProductDetailsAsync
                    val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryResult.productDetailsList.firstOrNull()
                    } else {
                        null
                    }
                    if (found != null) details = found
                    cont.resume(found?.oneTimePurchaseOfferDetails?.formattedPrice)
                }
            }
        }
    }

    override suspend fun tipPriceText(): String? = withContext(Dispatchers.Main.immediate) {
        tipDetails?.let { return@withContext it.oneTimePurchaseOfferDetails?.formattedPrice }
        if (!ensureConnected()) return@withContext null
        val found = queryDetails(TIP_PRODUCT_ID)
        if (found != null) tipDetails = found
        found?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    override fun launchTip(activity: Activity): Boolean {
        val product = tipDetails ?: return false
        lastLaunchWasTip = true
        return launchFlow(activity, product)
    }

    private suspend fun queryDetails(productId: String): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        return withTimeoutOrNull(BILLING_TIMEOUT_MS) {
            suspendCancellableCoroutine<ProductDetails?> { cont ->
                client.queryProductDetailsAsync(params) { result, queryResult ->
                    if (!cont.isActive) return@queryProductDetailsAsync
                    cont.resume(
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            queryResult.productDetailsList.firstOrNull()
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun launchFlow(activity: Activity, product: ProductDetails): Boolean {
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
            )
            .build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Платёж поддержки расходуется сразу: его можно повторить, и он ничего не включает. */
    private fun consumeIfPurchased(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { _, _ ->
                /* при ошибке повторим на следующей проверке покупок (onResume) */
            }
        }
    }

    override fun launchPurchase(activity: Activity): Boolean {
        val product = details ?: return false
        lastLaunchWasTip = false
        return launchFlow(activity, product)
    }

    private fun isGenuine(purchase: Purchase): Boolean =
        signature.isValid(purchase.originalJson, purchase.signature)

    /** Без подтверждения в течение 3 дней Google автоматически вернёт деньги. PENDING подтверждать нельзя. */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { /* при ошибке повторим на следующей проверке покупок (onResume) */ }
        }
    }

    /** Одно подключение за раз: остальные вызовы ждут его результата, а не запускают своё. */
    private suspend fun ensureConnected(): Boolean = connectLock.withLock {
        if (client.isReady) return@withLock true
        withTimeoutOrNull(BILLING_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            // С enableAutoServiceReconnection библиотека переподключится сама при следующем вызове.
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            }
        } ?: false
    }

    companion object {
        private const val BILLING_TIMEOUT_MS = 15_000L

        /**
         * Должен совпадать с идентификатором товара в Play Console.
         * [ТРЕБУЕТСЯ ВЫ] Создайте разовый товар `pro_lifetime` в Play Console (Монетизация → Товары в приложении)
         * и задайте цену. Пока товара нет, покупка в приложении недоступна.
         */
        const val PRODUCT_ID = "pro_lifetime"

        /**
         * Разовый расходуемый платёж «Поддержать разработчика».
         * [ТРЕБУЕТСЯ ВЫ] Создайте в Play Console товар `support_developer` (например, 1,99 €).
         * Пока товара нет, кнопка поддержки в настройках не показывается.
         */
        const val TIP_PRODUCT_ID = "support_developer"
    }
}
