package com.catsblock.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarParticleProjectionTest {
    @Test
    fun worldCenterPixelMapsToNearZeroLatitudeAndLongitude() {
        val (latitude, longitude) = radarPixelToLatLon(0, 0, 0, 127, 127)
        assertEquals(0.7031, latitude, 0.01)
        assertEquals(-0.703125, longitude, 0.001)
    }

    @Test
    fun longitudeIncreasesAcrossAdjacentWorldTiles() {
        val west = radarPixelToLatLon(1, 0, 0, 255, 127).second
        val east = radarPixelToLatLon(1, 1, 0, 0, 127).second
        assertTrue(east > west)
    }

    @Test
    fun longitudeRemainsWithinTheDatelineRange() {
        val westEdge = radarPixelToLatLon(1, 0, 0, 0, 127).second
        val eastEdge = radarPixelToLatLon(1, 1, 0, 255, 127).second
        assertTrue(westEdge >= -180.0)
        assertTrue(eastEdge < 180.0)
    }
}
