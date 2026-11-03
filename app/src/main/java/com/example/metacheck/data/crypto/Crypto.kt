package com.example.metacheck.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface Crypto {
    /** Может бросить [CryptoUnavailableException], если Keystore недоступен даже с новым ключом. */
    fun encrypt(plain: ByteArray): ByteArray

    /** Бросает исключение для повреждённых данных или данных, ключ которых больше не работает. */
    fun decrypt(blob: ByteArray): ByteArray
}

/** Хранилище секретных ключей. В приложении это Android Keystore, в тестах — обычная память. */
interface SecretKeyStore {
    fun aliases(): List<String>
    fun get(alias: String): SecretKey?
    fun create(alias: String): SecretKey
}

/** AES-256 в Android Keystore: ключ не покидает защищённое хранилище. */
class AndroidSecretKeyStore : SecretKeyStore {
    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun aliases(): List<String> = keyStore().aliases().toList()

    override fun get(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    override fun create(alias: String): SecretKey {
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

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
    }
}

/**
 * AES-256-GCM с ключом в Android Keystore. Формат блоба: 12 байт IV + шифртекст с тегом
 * (тот же, что в прежних версиях: уже сохранённые данные читаются без миграции).
 *
 * Связка ключей вместо одного ключа (аудит, п. 22). Раньше при трёх неудачных попытках шифрования
 * ключ удалялся и создавался заново. Этим ключом зашифровано всё: история, лицензия Pro, ключ API,
 * счётчик лимита и кэш покупки. Временный сбой keystore2 (а он бывает под нагрузкой, после
 * обновления системы или на отдельных прошивках) безвозвратно уничтожал все эти данные.
 *
 * Теперь ключи никогда не удаляются:
 *  - encrypt: активный (самый новый) ключ → повтор с перечитанным ключом → новый ключ
 *    с алиасом `<base>_r<N>`, старые остаются. Не больше [MAX_KEYS] ключей; дальше — [CryptoUnavailableException];
 *  - decrypt: пробует ключи от новых к старым. Неверный ключ GCM распознаёт по тегу, поэтому
 *    формат блоба менять не нужно. Если старый ключ «ожил» после сбоя, его данные снова читаются.
 */
class KeystoreCrypto(
    private val store: SecretKeyStore = AndroidSecretKeyStore(),
    private val baseAlias: String = "metacheck_aes_key",
) : Crypto {

    private val lock = Any()
    private val cache = HashMap<String, SecretKey>()

    @Volatile
    private var ringCache: List<String>? = null

    override fun encrypt(plain: ByteArray): ByteArray {
        val errors = ArrayList<Exception>()
        // Список ключей не прочитан — новый ключ не создаём: Keystore перезаписал бы ключ с тем же
        // алиасом, если он на самом деле существует.
        val ring = try { ring() } catch (e: Exception) { throw CryptoUnavailableException(e) }
        val active = ring.lastOrNull()
        if (active != null) {
            repeat(2) { attempt ->
                try {
                    return encryptWith(active, plain)
                } catch (e: Exception) {
                    errors += e
                    // Второй раз — с ключом, перечитанным из Keystore (временный сбой keystore2).
                    if (attempt == 0) forget(active)
                }
            }
        }
        // Активный ключ не работает или его нет: заводим новый, старые не трогаем.
        try {
            val next = nextAlias(ring) ?: throw IllegalStateException("Key ring is full ($MAX_KEYS keys)")
            synchronized(lock) {
                cache[next] = store.create(next)
                ringCache = null
            }
            return encryptWith(next, plain)
        } catch (e: Exception) {
            errors.forEach { e.addSuppressed(it) }
            throw CryptoUnavailableException(e)
        }
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size >= IV_SIZE + TAG_BITS / 8) { "Corrupted ciphertext" }
        var last: Exception? = null
        // Два прохода: во втором кэш ключей сброшен (временный сбой keystore2).
        repeat(2) { pass ->
            if (pass == 1) synchronized(lock) { cache.clear(); ringCache = null }
            val aliases = try { ring().asReversed() } catch (e: Exception) { last = e; emptyList() }
            for (alias in aliases) {
                try {
                    return decryptWith(alias, blob)
                } catch (e: Exception) {
                    last = e
                }
            }
        }
        throw last ?: IllegalStateException("No keys in key ring")
    }

    private fun encryptWith(alias: String, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decryptWith(alias: String, blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE))
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    private fun key(alias: String): SecretKey {
        synchronized(lock) { cache[alias]?.let { return it } }
        val key = store.get(alias) ?: throw IllegalStateException("Key $alias is missing")
        synchronized(lock) { cache[alias] = key }
        return key
    }

    private fun forget(alias: String) {
        synchronized(lock) { cache.remove(alias) }
    }

    /** Алиасы связки по возрастанию номера: `<base>`, `<base>_r1`, `<base>_r2`... */
    private fun ring(): List<String> {
        ringCache?.let { return it }
        val found = store.aliases().mapNotNull { alias -> indexOf(alias)?.let { it to alias } }
            .sortedBy { it.first }
            .map { it.second }
        ringCache = found
        return found
    }

    private fun indexOf(alias: String): Int? = when {
        alias == baseAlias -> 0
        alias.startsWith("${baseAlias}_r") -> alias.removePrefix("${baseAlias}_r").toIntOrNull()?.takeIf { it > 0 }
        else -> null
    }

    private fun nextAlias(existing: List<String>): String? {
        if (existing.size >= MAX_KEYS) return null
        val next = (existing.mapNotNull(::indexOf).maxOrNull() ?: -1) + 1
        return if (next == 0) baseAlias else "${baseAlias}_r$next"
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        /** Предел защищает от бесконечного создания ключей на устройстве с полностью сломанным Keystore. */
        const val MAX_KEYS = 4
    }
}

/** Keystore недоступен даже с новым ключом. Вызывающий код должен работать без шифрования/кэша. */
class CryptoUnavailableException(cause: Throwable) : Exception("Android Keystore unavailable", cause)
