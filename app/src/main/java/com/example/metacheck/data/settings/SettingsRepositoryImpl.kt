package com.example.metacheck.data.settings

import android.content.SharedPreferences
import android.util.Base64
import com.example.metacheck.data.crypto.Crypto
import com.example.metacheck.domain.model.AppSettings
import com.example.metacheck.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Обычные настройки лежат в SharedPreferences, ключ API только в зашифрованном виде
 * (AES-GCM, ключ в Android Keystore). Открытым текстом ключ нигде не хранится.
 */
class SettingsRepositoryImpl(
    private val prefs: SharedPreferences,
    private val crypto: Crypto,
) : SettingsRepository {

    private val state = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val next = transform(state.value).copy(hasApiKey = state.value.hasApiKey)
            prefs.edit()
                .putBoolean(K_HISTORY, next.saveHistory)
                .putBoolean(K_API_ON, next.apiEnabled)
                .putString(K_ENDPOINT, next.apiEndpoint.trim())
                .putString(K_HEADER, next.apiHeaderName.trim())
                .putBoolean(K_SEND_GPS, next.apiSendGps)
                .putBoolean(K_TLS13, next.tls13Only)
                .putString(K_PIN, next.certPin.trim())
                .apply()
            state.value = next
        }
    }

    override fun saveApiKey(key: String?) {
        synchronized(this) {
            val editor = prefs.edit()
            if (key.isNullOrEmpty()) {
                editor.remove(K_KEY)
            } else {
                val blob = crypto.encrypt(key.toByteArray(Charsets.UTF_8))
                editor.putString(K_KEY, Base64.encodeToString(blob, Base64.NO_WRAP))
            }
            editor.apply()
            state.value = state.value.copy(hasApiKey = !key.isNullOrEmpty())
        }
    }

    override fun readApiKey(): String? =
        try {
            prefs.getString(K_KEY, null)?.let {
                String(crypto.decrypt(Base64.decode(it, Base64.NO_WRAP)), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }

    private fun load() = AppSettings(
        saveHistory = prefs.getBoolean(K_HISTORY, true),
        apiEnabled = prefs.getBoolean(K_API_ON, false),
        apiEndpoint = prefs.getString(K_ENDPOINT, "") ?: "",
        apiHeaderName = prefs.getString(K_HEADER, "Authorization") ?: "Authorization",
        apiSendGps = prefs.getBoolean(K_SEND_GPS, false),
        tls13Only = prefs.getBoolean(K_TLS13, false),
        certPin = prefs.getString(K_PIN, "") ?: "",
        hasApiKey = prefs.contains(K_KEY),
    )

    private companion object {
        const val K_HISTORY = "save_history"
        const val K_API_ON = "api_enabled"
        const val K_ENDPOINT = "api_endpoint"
        const val K_HEADER = "api_header"
        const val K_SEND_GPS = "api_send_gps"
        const val K_TLS13 = "api_tls13_only"
        const val K_PIN = "api_cert_pin"
        const val K_KEY = "api_key_enc"
    }
}
