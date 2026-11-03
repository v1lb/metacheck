package com.example.metacheck.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Хранилище в памяти; [broken] — алиасы, к которым Keystore временно не даёт доступ. */
private class FakeStore : SecretKeyStore {
    val keys = LinkedHashMap<String, SecretKey>()
    val broken = HashSet<String>()
    var aliasesFail = false
    var created = 0

    override fun aliases(): List<String> {
        if (aliasesFail) throw java.security.KeyStoreException("keystore2 busy")
        return keys.keys.toList()
    }

    override fun get(alias: String): SecretKey? {
        if (alias in broken) throw java.security.UnrecoverableKeyException("Failed to obtain information about key")
        return keys[alias]
    }

    override fun create(alias: String): SecretKey {
        created++
        return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { keys[alias] = it }
    }
}

class KeystoreCryptoTest {

    private val text = "history report".toByteArray()

    @Test fun roundTripCreatesBaseKeyOnce() {
        val store = FakeStore()
        val crypto = KeystoreCrypto(store)
        val blob = crypto.encrypt(text)
        assertArrayEquals(text, crypto.decrypt(blob))
        crypto.encrypt(text)
        assertEquals(listOf("metacheck_aes_key"), store.keys.keys.toList())
        assertEquals(1, store.created)
    }

    /** Аудит, п. 22: сбой ключа больше не стирает уже зашифрованные данные. */
    @Test fun brokenKeyIsRotatedNotDeleted() {
        val store = FakeStore()
        val old = KeystoreCrypto(store).encrypt(text)

        store.broken += "metacheck_aes_key"
        val crypto = KeystoreCrypto(store)
        val fresh = crypto.encrypt("new".toByteArray())
        assertEquals(listOf("metacheck_aes_key", "metacheck_aes_key_r1"), store.keys.keys.toList())
        assertArrayEquals("new".toByteArray(), crypto.decrypt(fresh))

        // Keystore «ожил»: старые данные снова читаются, ключ никто не удалял.
        store.broken.clear()
        assertArrayEquals(text, KeystoreCrypto(store).decrypt(old))
        assertArrayEquals("new".toByteArray(), KeystoreCrypto(store).decrypt(fresh))
    }

    @Test fun newestKeyIsUsedForEncryption() {
        val store = FakeStore()
        KeystoreCrypto(store).encrypt(text)
        store.broken += "metacheck_aes_key"
        KeystoreCrypto(store).encrypt(text) // создаёт _r1
        store.broken.clear()
        KeystoreCrypto(store).encrypt(text)
        assertEquals(2, store.keys.size) // _r1 рабочий, новый не нужен
    }

    @Test fun unreadableAliasListNeverCreatesKeys() {
        val store = FakeStore()
        val blob = KeystoreCrypto(store).encrypt(text)
        store.aliasesFail = true
        val crypto = KeystoreCrypto(store)
        assertTrue(runCatching { crypto.encrypt(text) }.exceptionOrNull() is CryptoUnavailableException)
        assertEquals(1, store.created) // иначе create() перезаписал бы существующий ключ
        store.aliasesFail = false
        assertArrayEquals(text, crypto.decrypt(blob))
    }

    @Test fun ringIsCapped() {
        val store = FakeStore()
        repeat(10) {
            store.broken += store.keys.keys
            runCatching { KeystoreCrypto(store).encrypt(text) }
        }
        assertEquals(4, store.keys.size)
        store.broken += store.keys.keys
        assertTrue(runCatching { KeystoreCrypto(store).encrypt(text) }.exceptionOrNull() is CryptoUnavailableException)
    }

    @Test fun corruptedBlobFails() {
        val store = FakeStore()
        val crypto = KeystoreCrypto(store)
        val blob = crypto.encrypt(text).also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(runCatching { crypto.decrypt(blob) }.isFailure)
        assertTrue(runCatching { crypto.decrypt(ByteArray(5)) }.isFailure)
    }

    @Test fun foreignAliasesAreIgnored() {
        val store = FakeStore()
        store.create("other_key"); store.create("metacheck_aes_key_rX")
        KeystoreCrypto(store).encrypt(text)
        assertTrue("metacheck_aes_key" in store.keys)
    }
}
