package com.example.metacheck.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PurchaseSignatureTest {

    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    // Play Console показывает ключ как base64 от X.509 SubjectPublicKeyInfo, иногда с переносами строк.
    private val publicKey = Base64.getMimeEncoder().encodeToString(pair.public.encoded)
    private val json = """{"orderId":"GPA.1234","productId":"pro_lifetime","purchaseState":0,"purchaseToken":"abc"}"""

    private fun sign(data: String): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("SHA1withRSA").run {
            initSign(pair.private); update(data.toByteArray()); sign()
        },
    )

    @Test fun genuinePurchaseIsAccepted() {
        val verifier = PurchaseSignature(publicKey)
        assertTrue(verifier.enabled)
        assertTrue(verifier.isValid(json, sign(json)))
    }

    /** Аудит, п. 26: подменённый ответ «куплено» не открывает Pro. */
    @Test fun forgedPurchasesAreRejected() {
        val verifier = PurchaseSignature(publicKey)
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val foreign = Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA1withRSA").run { initSign(other.private); update(json.toByteArray()); sign() },
        )
        assertFalse(verifier.isValid(json, foreign))
        assertFalse(verifier.isValid(json.replace("abc", "abd"), sign(json)))
        assertFalse(verifier.isValid(json, ""))
        assertFalse(verifier.isValid(json, "not base64 !!!"))
        assertFalse(verifier.isValid(json, Base64.getEncoder().encodeToString(ByteArray(10))))
        assertFalse(verifier.isValid("", sign(json)))
    }

    @Test fun blankKeyDisablesCheckForDebugBuilds() {
        val verifier = PurchaseSignature("  ")
        assertFalse(verifier.enabled)
        assertTrue(verifier.isValid(json, ""))
    }

    @Test fun brokenKeyRejectsEverythingInsteadOfCrashing() {
        val verifier = PurchaseSignature("definitely-not-a-key")
        assertTrue(verifier.enabled)
        assertFalse(verifier.isValid(json, sign(json)))
    }
}
