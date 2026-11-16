package com.catsblock.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityCityValidationTest {
    private val configured = City("Sao Paulo", "São Paulo", "Brazil", "BR", -23.5505, -46.6333)

    @Test
    fun sameCityWithAccentAndSmallCentroidDriftMatches() {
        val current = City("São Paulo", "Sao Paulo", "Brasil", "br", -23.5520, -46.6310)
        assertTrue(matchesActivityCity(configured, current))
    }

    @Test
    fun sameNameInDifferentCountryDoesNotMatch() {
        val other = City("São Paulo", "São Paulo", "Portugal", "PT", -23.5505, -46.6333)
        assertFalse(matchesActivityCity(configured, other))
    }

    @Test
    fun sameNameInDifferentRegionDoesNotMatch() {
        val other = City("Springfield", "Ohio", "United States", "US", 39.7817, -89.6501)
        val expected = City("Springfield", "Illinois", "United States", "US", 39.7817, -89.6501)
        assertFalse(matchesActivityCity(expected, other))
    }

    @Test
    fun nearbyDifferentCityDoesNotMatchEvenWithSameCountryAndRegion() {
        val city = City("Santo Andre", "São Paulo", "Brazil", "BR", -23.6600, -46.5300)
        assertFalse(matchesActivityCity(configured, city))
    }

    @Test
    fun nonLatinCityNamesRemainComparable() {
        val one = City("Αθήνα", "Αττική", "Ελλάδα", "GR", 37.9838, 23.7275)
        val two = City("Αθήνα", "Αττική", "Ελλάδα", "gr", 37.9840, 23.7276)
        assertTrue(matchesActivityCity(one, two))
    }
}
