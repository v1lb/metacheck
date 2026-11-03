package com.example.metacheck.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Аудит, п. 10: идентифицирующий User-Agent и корректные адреса тайлов MapTiler. */
class MapTileUrlsTest {
    @Test fun userAgentNamesAppVersionAndContact() {
        val ua = MapTileUrls.userAgent("2.6.1")
        assertTrue(ua.startsWith("MetaCheck/2.6.1 "))
        assertTrue(ua.contains("https://"))
        assertFalse("не голое имя пакета", ua == "dev.metacheck.app")
    }

    @Test fun userAgentWithoutVersion() {
        assertTrue(MapTileUrls.userAgent(null).startsWith("MetaCheck/unknown "))
        assertTrue(MapTileUrls.userAgent(" ").startsWith("MetaCheck/unknown "))
    }

    @Test fun mapTilerUrlFormat() {
        assertEquals(
            "https://api.maptiler.com/maps/streets-v2/256/15/17520/12180.png?key=abc123",
            MapTileUrls.mapTilerUrl("abc123", 15, 17520, 12180),
        )
    }

    @Test fun keyIsUrlEncoded() {
        assertTrue(MapTileUrls.mapTilerUrl("a b&c", 1, 0, 0).endsWith("?key=a+b%26c"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyKeyRejected() { MapTileUrls.mapTilerUrl(" ", 1, 0, 0) }

    @Test(expected = IllegalArgumentException::class)
    fun badIndexRejected() { MapTileUrls.mapTilerUrl("k", 30, 0, 0) }
}
