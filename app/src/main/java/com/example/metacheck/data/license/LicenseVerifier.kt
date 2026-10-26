package com.example.metacheck.data.license

import com.example.metacheck.domain.model.LicenseException
import com.example.metacheck.domain.model.LicenseInfo
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Проверка офлайн-ключей Pro. Формат: `MC1.<payload base64url>.<подпись base64url>`,
 * подпись ECDSA P-256 / SHA-256 (DER) над строкой `MC1.<payload>`.
 * Payload: {"id": "...", "exp": секунды Unix (необязательно), "note": "..."}.
 * Выдаёт ключи только владелец закрытого ключа (см. tools/license_tool.py).
 */
class LicenseVerifier(
    private val publicKeyBase64: String,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    fun verify(token: String): Result<LicenseInfo> =
        try {
            Result.success(verifyOrThrow(token))
        } catch (e: LicenseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(LicenseException("Некорректный ключ"))
        }

    private fun verifyOrThrow(raw: String): LicenseInfo {
        if (publicKeyBase64.isBlank()) throw LicenseException("Ключи Pro в этой сборке не включены")
        val token = raw.trim()
        if (token.length > MAX_TOKEN_CHARS) throw LicenseException("Некорректный ключ")
        val parts = token.split('.')
        if (parts.size != 3 || parts[0] != PREFIX) throw LicenseException("Некорректный ключ")

        val signed = (parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII)
        val signature = Base64.getUrlDecoder().decode(parts[2])
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(signed)
        if (!verifier.verify(signature)) throw LicenseException("Подпись ключа неверна")

        val payload = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
        val id = payload.optString("id")
        if (id.isBlank() || id.length > 64) throw LicenseException("Некорректный ключ")
        val expiresAt = if (payload.has("exp")) payload.getLong("exp") else null
        if (expiresAt != null && nowSeconds() > expiresAt) throw LicenseException("Срок действия ключа истёк")
        return LicenseInfo(id, expiresAt, payload.optString("note").take(100).ifBlank { null })
    }

    private companion object {
        const val PREFIX = "MC1"
        const val MAX_TOKEN_CHARS = 2048
    }
}
