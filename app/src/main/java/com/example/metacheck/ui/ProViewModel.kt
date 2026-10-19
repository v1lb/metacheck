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

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun openPaywall() {
        _info.value = null
        _paywallVisible.value = true
        viewModelScope.launch { _price.value = repository.priceText() }
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
