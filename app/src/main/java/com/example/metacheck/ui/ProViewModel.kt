package com.example.metacheck.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.metacheck.MetaCheckApplication
import com.example.metacheck.domain.model.Entitlement
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

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info.asStateFlow()

    /** Сообщения о покупке для snackbar (показываются и при закрытом окне Pro). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    private fun messageFor(event: PurchaseEvent): String? = when (event) {
        PurchaseEvent.COMPLETED -> "Pro активирован. Спасибо за покупку!"
        PurchaseEvent.PENDING -> "Платёж ожидает подтверждения. Pro включится автоматически после оплаты."
        PurchaseEvent.ALREADY_OWNED -> "Покупка Pro восстановлена"
        PurchaseEvent.FAILED -> "Покупка не завершена. Попробуйте ещё раз позже."
        PurchaseEvent.CANCELED, PurchaseEvent.UPDATED -> null
    }

    fun openPaywall() {
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
            _info.value = "Не удалось открыть покупку. Проверь Google Play и интернет."
        }
    }

    fun redeem(token: String) {
        viewModelScope.launch {
            repository.redeem(token).fold(
                onSuccess = {
                    _info.value = "Ключ принят"
                    _paywallVisible.value = false
                },
                onFailure = { _info.value = it.message ?: "Некорректный ключ" },
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
