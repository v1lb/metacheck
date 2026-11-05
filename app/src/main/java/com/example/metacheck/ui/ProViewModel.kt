package com.example.metacheck.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.metacheck.MetaCheckApplication
import com.example.metacheck.R
import com.example.metacheck.ui.text.ErrorTexts
import com.example.metacheck.ui.text.UiText
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.LicenseException
import com.example.metacheck.domain.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import com.example.metacheck.domain.model.PurchaseEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProViewModel(private val repository: EntitlementRepository) : ViewModel() {

    val entitlement: StateFlow<Entitlement> = repository.entitlement

    private val _paywallVisible = MutableStateFlow(false)
    val paywallVisible: StateFlow<Boolean> = _paywallVisible.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _tipPrice = MutableStateFlow<String?>(null)
    /** Цена «Поддержать разработчика». null: товар не создан в Play Console или Play недоступен, кнопку не показываем. */
    val tipPrice: StateFlow<String?> = _tipPrice.asStateFlow()

    /** Окно Pro открыто из-за исчерпанного дневного лимита: показываем это первой строкой. */
    private val _limitReached = MutableStateFlow(false)
    val limitReached: StateFlow<Boolean> = _limitReached.asStateFlow()

    private val _info = MutableStateFlow<UiText?>(null)
    val info: StateFlow<UiText?> = _info.asStateFlow()

    /** Сообщения о покупке для snackbar (показываются и при закрытом окне Pro). */
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            repository.purchaseEvents.collect { event ->
                messageFor(event)?.let { _message.value = it }
                if (event == PurchaseEvent.COMPLETED || event == PurchaseEvent.ALREADY_OWNED) _paywallVisible.value = false
            }
        }
    }

    /**
     * Сверка с Google Play. Вызывается на каждом ON_RESUME: так подхватываются отложенные платежи,
     * оплаченные вне приложения, и возвраты денег. Первый вызов приходится на старт экрана.
     */
    fun refresh() {
        viewModelScope.launch {
            // refresh() по контракту не бросает, try/catch оставлен как второй рубеж.
            try { repository.refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun messageFor(event: PurchaseEvent): UiText? = when (event) {
        PurchaseEvent.COMPLETED -> UiText.Res(R.string.pro_msg_completed)
        PurchaseEvent.PENDING -> UiText.Res(R.string.pro_payment_pending)
        PurchaseEvent.ALREADY_OWNED -> UiText.Res(R.string.pro_msg_restored)
        PurchaseEvent.FAILED -> UiText.Res(R.string.pro_msg_failed)
        PurchaseEvent.TIP_COMPLETED -> UiText.Res(R.string.tip_thanks)
        PurchaseEvent.CANCELED, PurchaseEvent.UPDATED -> null
    }

    fun loadTipPrice() {
        viewModelScope.launch {
            _tipPrice.value = try { repository.tipPriceText() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    fun tip(activity: Activity) {
        if (!repository.launchTip(activity)) _message.value = UiText.Res(R.string.pro_msg_launch_failed)
    }

    /** Дневной лимит бесплатной очистки исчерпан: предлагаем Pro, без давления. */
    fun openPaywallForLimit() {
        openPaywall()
        _limitReached.value = true
    }

    fun openPaywall() {
        _limitReached.value = false
        _info.value = null
        _paywallVisible.value = true
        viewModelScope.launch {
            _price.value = try { repository.priceText() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    fun dismissPaywall() {
        _paywallVisible.value = false
    }

    fun buy(activity: Activity) {
        if (!repository.launchPurchase(activity)) {
            _info.value = UiText.Res(R.string.pro_msg_launch_failed)
        }
    }

    fun redeem(token: String) {
        viewModelScope.launch {
            repository.redeem(token).fold(
                onSuccess = {
                    _info.value = UiText.Res(R.string.pro_msg_key_accepted)
                    _paywallVisible.value = false
                },
                onFailure = {
                    // Общий текст ErrorTexts говорит о файлах; для ключа уместно «некорректный ключ».
                    _info.value = if (it is LicenseException) ErrorTexts.reason(it) else UiText.Res(R.string.license_error_malformed)
                },
            )
        }
    }

    fun removeLicense() = repository.removeLicense()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MetaCheckApplication).container
                ProViewModel(c.entitlements)
            }
        }
    }
}
