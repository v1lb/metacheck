package com.example.metacheck.data.metadata

import java.io.InputStream
import java.security.MessageDigest

/** Independent digest check after persisting output through a Storage Access Framework provider. */
internal object WriteIntegrity {
    fun matches(expected: InputStream, persisted: InputStream): Boolean =
        MessageDigest.isEqual(sha256(expected), sha256(persisted))

    private fun sha256(input: InputStream): ByteArray = input.use { source ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest()
    }
}
