package com.example.metacheck.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Аудит, п. 12: декодирование не должно выходить за свободную память процесса. */
class DecodeBudgetTest {
    private val mb = 1024L * 1024
    private val max = 16_000_000L

    private fun bytes(w: Int, h: Int, sample: Int, copies: Int) = (w / sample).toLong() * (h / sample) * 4 * copies

    @Test fun plentyOfMemory_keepsFullResolution() {
        assertEquals(1, DecodeBudget.sampleSize(4000, 3000, max, copies = 2, availableBytes = 512 * mb))
    }

    @Test fun pixelLimitStillApplies() {
        // 50 Мп панорама: даже при огромной памяти не больше 16 Мп.
        assertEquals(2, DecodeBudget.sampleSize(10000, 5000, max, copies = 1, availableBytes = 4096 * mb))
    }

    @Test fun lowMemoryPhone_rotatedPhotoIsDownscaledToFit() {
        // 12 Мп с поворотом (2 копии = 96 МБ), а свободно 120 МБ (бюджет 60 МБ).
        val s = DecodeBudget.sampleSize(4000, 3000, max, copies = 2, availableBytes = 120 * mb)
        assertEquals(2, s)
        assertTrue(bytes(4000, 3000, s, 2) <= 60 * mb)
    }

    @Test fun resultAlwaysFitsBudget() {
        for (avail in listOf(16L, 40L, 96L, 200L, 380L)) for ((w, h) in listOf(4000 to 3000, 8160 to 6120, 12000 to 9000)) {
            for (copies in 1..2) {
                val s = DecodeBudget.sampleSize(w, h, max, copies, avail * mb)
                assertTrue("w=$w h=$h avail=$avail copies=$copies s=$s", bytes(w, h, s, copies) <= avail * mb / 2)
            }
        }
    }

    @Test fun noMemoryInfo_returnsBoundedSample() {
        assertEquals(1024, DecodeBudget.sampleSize(4000, 3000, max, copies = 1, availableBytes = 0))
    }
}
