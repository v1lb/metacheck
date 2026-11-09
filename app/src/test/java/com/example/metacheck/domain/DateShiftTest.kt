package com.example.metacheck.domain

import com.example.metacheck.domain.metadata.DateShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateShiftTest {
    @Test fun shiftsAcrossMidnightAndBack() {
        assertEquals("2024:06:16 01:12:10", DateShift.shift("2024:06:15 23:42:10", 90 * 60))
        assertEquals("2024:12:31 22:00:00", DateShift.shift("2025:01:01 01:00:00", -3 * 3600))
    }

    @Test fun badDatesAreSkipped() {
        assertNull(DateShift.shift("0000:00:00 00:00:00", 3600))
        assertNull(DateShift.shift("", 3600))
        assertNull(DateShift.shift("2024-06-15", 3600))
    }

    @Test fun formInput() {
        assertEquals(-5400L, DateShift.seconds(true, "1", "30"))
        assertEquals(7200L, DateShift.seconds(false, "2", ""))
        assertNull(DateShift.seconds(false, "", ""))
        assertNull(DateShift.seconds(false, "1", "75"))
        assertNull(DateShift.seconds(false, "9000", "0"))
    }
}
