package com.example.metacheck.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface Crypto {
    /** Может бросить исключение, если Keystore недоступен даже после восстановления ключа. */
    fun encrypt(plain: ByteArray): ByteArray

    /** Бросает исключение для повреждённых данных или данных, зашифрованных потерянным ключом. */
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM, ключ живёт в Android Keystore и не покидает защищённое хранилище.
 * Формат блоба: 12 байт IV + шифртекст с тегом.
 *
 * Самовосстановление. На части устройств Keystore ломается: после обновления ОС, смены прошивки,
 * сбоя keystore2 и т. п. Тогда getKey()/init() бросают KeyStoreException, UnrecoverableKeyException,
 * InvalidKeyException или ProviderException. Раньше это приводило к крэшу при каждом запуске.
 *
 * Политика:
 *  - encrypt: попытка → повтор с перечитанным ключом (временный сбой) → удаление alias,
 *    новый ключ, последняя попытка. Данные, зашифрованные потерянным ключом, и так нечитаемы,
 *    поэтому пересоздание ничего не теряет.
 *  - decrypt: ключ НЕ удаляется. Ошибка расшифровки может означать просто повреждённый файл,
 *    удаление ключа из-за него уничтожило бы все остальные данные. Ошибка уходит вызывающему коду.
 */
class KeystoreCrypto(private val alias: String = "metacheck_aes_key") : Crypto {

    private val lock = Any()

    @Volatile
    private var cached: SecretKey? = null

    override fun encrypt(plain: ByteArray): ByteArray {
        try {
            return encryptOnce(plain)
        } catch (first: Exception) {
            dropCachedKey()
            try {
                return encryptOnce(plain)
            } catch (second: Exception) {
                resetKey()
                try {
                    return encryptOnce(plain)
                } catch (last: Exception) {
                    last.addSuppressed(first)
                    last.addSuppressed(second)
                    throw CryptoUnavailableException(last)
                }
            }
        }
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size >= IV_SIZE + TAG_BITS / 8) { "Corrupted ciphertext" }
        return try {
            decryptOnce(blob)
        } catch (first: Exception) {
            // Повторяем один раз с перечитанным ключом (временный сбой keystore2), ключ не трогаем.
            dropCachedKey()
            decryptOnce(blob)
        }
    }

    private fun encryptOnce(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decryptOnce(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE))
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    private fun key(): SecretKey {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val existing = keyStore().getKey(alias, null) as? SecretKey
            return (existing ?: generate()).also { cached = it }
        }
    }

    private fun generate(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun dropCachedKey() {
        synchronized(lock) { cached = null }
    }

    private fun resetKey() {
        synchronized(lock) {
            cached = null
            runCatching { keyStore().deleteEntry(alias) }
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}

/** Keystore недоступен даже после пересоздания ключа. Вызывающий код должен работать без шифрования/кэша. */
class CryptoUnavailableException(cause: Throwable) : Exception("Android Keystore unavailable", cause)
