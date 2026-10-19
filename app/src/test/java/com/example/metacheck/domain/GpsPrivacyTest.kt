package com.example.metacheck.domain

import com.example.metacheck.domain.metadata.GpsPrivacy
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.sampleReport
import org.junit.Assert.*
import org.junit.Test

class GpsPrivacyTest {
    @Test fun detectsExifLocationOnlyIfActuallyPresent() {
        assertFalse(GpsPrivacy.hasExifLocation(sampleReport(ai = false, gps = false)))
        val report = sampleReport(ai = false, gps = false).copy(gps = GpsPoint(41.9028,12.4964,null))
        assertTrue(GpsPrivacy.hasExifLocation(report))
        val xmpOnly = sampleReport(ai = false, gps = false).copy(
            xmpItems = listOf(com.example.metacheck.domain.model.MetaItem("GPSLatitude (XMP)", "41.9")))
        assertTrue(GpsPrivacy.hasLocationMarkers(xmpOnly))
    }

    @Test fun exactCoordinateFormattingAndProjection() {
        assertEquals("41.902800°, 12.496400°", GpsPrivacy.label(41.9028,12.4964))
        assertEquals(.5f, GpsPrivacy.mapX(12.4964,12.4964,110.0), .00001f)
        assertEquals(.5f, GpsPrivacy.mapY(41.9028,41.9028,70.0), .00001f)
        assertEquals(2.0, GpsPrivacy.longitudeDelta(-179.0,179.0), .000001)
        assertEquals(-2.0, GpsPrivacy.longitudeDelta(179.0,-179.0), .000001)
    }

    @Test fun invalidCoordinatesAreRejected() {
        assertFalse(GpsPrivacy.validCoordinates(Double.NaN, 0.0))
        assertFalse(GpsPrivacy.validCoordinates(100.0, 0.0))
        assertFalse(GpsPrivacy.validCoordinates(40.0, 181.0))
        assertTrue(GpsPrivacy.validCoordinates(-90.0, -180.0))
    }
}
