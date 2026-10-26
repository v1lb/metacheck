package com.example.metacheck.data.license

import com.example.metacheck.domain.model.LicenseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseVerifierTest {

    private fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun b64u(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun publicKeyOf(kp: KeyPair) = Base64.getEncoder().encodeToString(kp.public.encoded)

    /** Выпуск ключа так же, как это делает tools/license_tool.py. */
    private fun issue(kp: KeyPair, payload: String): String {
        val head = "MC1." + b64u(payload.toByteArray(Charsets.UTF_8))
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(kp.private)
            update(head.toByteArray(Charsets.US_ASCII))
        }.sign()
        return head + "." + b64u(sig)
    }

    private val kp = newKeyPair()
    private fun verifier(now: Long = 1_000L, key: KeyPair = kp) = LicenseVerifier(publicKeyOf(key)) { now }

    private fun message(r: Result<*>) = (r.exceptionOrNull() as? LicenseException)?.message

    @Test
    fun acceptsValidLifetimeKey() {
        val info = verifier().verify(issue(kp, """{"id":"abc123","note":"Иван"}""")).getOrThrow()
        assertEquals("abc123", info.id)
        assertNull(info.expiresAt)
        assertEquals("Иван", info.note)
    }

    @Test
    fun acceptsKeyBeforeExpiry() {
        val info = verifier(now = 1_000).verify(issue(kp, """{"id":"a","exp":2000}""")).getOrThrow()
        assertEquals(2000L, info.expiresAt)
    }

    @Test
    fun rejectsExpiredKey() {
        val r = verifier(now = 3_000).verify(issue(kp, """{"id":"a","exp":2000}"""))
        assertEquals("Срок действия ключа истёк", message(r))
    }

    @Test
    fun rejectsTamperedPayload() {
        val good = issue(kp, """{"id":"a","exp":2000}""")
        val parts = good.split('.')
        val forged = "MC1." + b64u("""{"id":"a"}""".toByteArray()) + "." + parts[2] // подпись от другого текста
        assertEquals("Подпись ключа неверна", message(verifier().verify(forged)))
    }

    @Test
    fun rejectsKeyFromAnotherIssuer() {
        val other = newKeyPair()
        val r = verifier().verify(issue(other, """{"id":"a"}"""))
        assertEquals("Подпись ключа неверна", message(r))
    }

    @Test
    fun rejectsGarbage() {
        for (bad in listOf("", "abc", "MC1.only.two", "XX1.a.b", "MC1.!!!.???", "MC1..", "a.b.c.d")) {
            assertTrue("должно быть отклонено: $bad", verifier().verify(bad).isFailure)
        }
    }

    @Test
    fun rejectsMissingOrHugeId() {
        assertTrue(verifier().verify(issue(kp, """{"note":"x"}""")).isFailure)
        assertTrue(verifier().verify(issue(kp, """{"id":"${"a".repeat(65)}"}""")).isFailure)
    }

    @Test
    fun rejectsOversizedToken() {
        assertTrue(verifier().verify("MC1." + "A".repeat(3000) + ".AA").isFailure)
    }

    @Test
    fun disabledWithoutPublicKey() {
        val r = LicenseVerifier("").verify(issue(kp, """{"id":"a"}"""))
        assertEquals("Ключи Pro в этой сборке не включены", message(r))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertTrue(verifier().verify("  " + issue(kp, """{"id":"a"}""") + "\n").isSuccess)
    }
}
