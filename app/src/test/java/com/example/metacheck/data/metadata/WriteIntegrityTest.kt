package com.example.metacheck.data.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WriteIntegrityTest {
    private fun bytes(content: ByteArray) = ByteArrayInputStream(content)

    @Test fun unchangedCopyMatches() {
        val original = ByteArray(2 * 1024 * 1024) { (it % 253).toByte() }
        assertTrue(WriteIntegrity.matches(bytes(original), bytes(original.clone())))
    }

    @Test fun truncatedCopyNeverMatches() {
        val original = ByteArray(100_000) { (it % 199).toByte() }
        assertFalse(WriteIntegrity.matches(bytes(original), bytes(original.copyOf(original.size - 1))))
    }

    @Test fun singleByteChangeNeverMatches() {
        val original = ByteArray(500_000) { (it % 137).toByte() }
        val modified = original.clone()
        modified[12345] = (modified[12345] + 1).toByte()
        assertFalse(WriteIntegrity.matches(bytes(original), bytes(modified)))
    }

    @Test fun emptyCopiesMatch() {
        assertTrue(WriteIntegrity.matches(bytes(byteArrayOf()), bytes(byteArrayOf())))
    }
}
