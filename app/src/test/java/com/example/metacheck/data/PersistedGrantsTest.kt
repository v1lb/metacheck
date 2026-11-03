package com.example.metacheck.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Аудит, п. 14: постоянных прав не больше лимита, отпускаются самые старые. */
class PersistedGrantsTest {
    @Test fun underLimit_releasesNothing() {
        assertTrue(PersistedGrants.toRelease(listOf("a" to 1L, "b" to 2L), keep = 32).isEmpty())
    }

    @Test fun overLimit_releasesOldest() {
        val grants = (1..40).map { "uri$it" to it.toLong() }
        val release = PersistedGrants.toRelease(grants, keep = 32)
        assertEquals((1..8).map { "uri$it" }.toSet(), release)
    }

    @Test fun unorderedInput() {
        val release = PersistedGrants.toRelease(listOf("new" to 300L, "old" to 100L, "mid" to 200L), keep = 2)
        assertEquals(setOf("old"), release)
    }

    @Test fun zeroKeep_releasesAll() {
        assertEquals(setOf("a", "b"), PersistedGrants.toRelease(listOf("a" to 1L, "b" to 2L), keep = 0))
    }
}
