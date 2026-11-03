package com.example.metacheck.data.billing

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Проверка подписи покупки Google Play на устройстве (аудит, п. 26).
 *
 * Google подписывает JSON покупки (Purchase.originalJson) ключом приложения, подпись SHA1withRSA
 * лежит в Purchase.signature. Без этой проверки приложение верило любому ответу «куплено»,
 * и подменённый ответ Play открывал Pro бесплатно.
 *
 * Ограничение: проверка на устройстве останавливает готовые подделки покупок, но не взлом
 * самого APK. Полная защита — проверка токена на своём сервере через Google Play Developer API.
 */
class PurchaseSignature(publicKeyBase64: String) {

    private val configured = publicKeyBase64.isNotBlank()

    /** Испорченный ключ не роняет приложение при запуске: тогда ни одна покупка не считается настоящей. */
    private val key: PublicKey? = if (!configured) null else try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getMimeDecoder().decode(publicKeyBase64.trim())))
    } catch (_: Exception) {
        null
    }

    /** Ключ не задан: проверять нечем (только отладочные сборки, релиз без ключа не собирается). */
    val enabled: Boolean get() = configured

    fun isValid(signedData: String, signatureBase64: String): Boolean {
        if (!configured) return true
        val publicKey = key ?: return false
        if (signedData.isEmpty() || signatureBase64.isBlank()) return false
        return try {
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signatureBase64.trim()))
            }
        } catch (_: Exception) {
            false // испорченный base64 или подпись другой длины — это не настоящая покупка
        }
    }

    private companion object {
        const val ALGORITHM = "SHA1withRSA"
    }
}
