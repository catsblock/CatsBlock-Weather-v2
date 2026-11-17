package com.catsblock.weather.data

import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindDirectionTest {
    @Test
    fun normalizesAnglesIntoTheGeographicRange() {
        assertEquals(0.0, normalizeWindDirection(0.0)!!, 0.0)
        assertEquals(0.0, normalizeWindDirection(360.0)!!, 0.0)
        assertEquals(315.0, normalizeWindDirection(-45.0)!!, 0.0)
        assertEquals(5.0, normalizeWindDirection(725.0)!!, 0.0)
    }

    @Test
    fun missingAndNonFiniteDirectionsRemainUnavailable() {
        assertNull(normalizeWindDirection(null))
        assertNull(normalizeWindDirection(Double.NaN))
        assertNull(normalizeWindDirection(Double.POSITIVE_INFINITY))
        assertNull(normalizeWindDirection(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun cardinalAndIntercardinalAnglesKeepGeographicMeanings() {
        val previous = I18n.lang
        I18n.lang = AppLanguage.English
        try {
            val cases = listOf(
                0.0 to "N",
                45.0 to "NE",
                90.0 to "E",
                135.0 to "SE",
                180.0 to "S",
                225.0 to "SW",
                270.0 to "W",
                315.0 to "NW"
            )
            cases.forEach { (degrees, expected) ->
                assertEquals(expected, compassLabel(normalizeWindDirection(degrees)!!))
            }
        } finally {
            I18n.lang = previous
        }
    }

    @Test
    fun circularMeanCrossesNorthAndIgnoresUnavailableDirections() {
        assertEquals(0.0, circularMeanWindDirection(listOf(350.0, 10.0, null))!!, 1e-8)
        assertEquals(90.0, circularMeanWindDirection(listOf(null, 90.0, Double.NaN))!!, 1e-8)
    }

    @Test
    fun emptyOrOpposingDirectionsStayUnavailable() {
        assertNull(circularMeanWindDirection(emptyList()))
        assertNull(circularMeanWindDirection(listOf(null, Double.NaN)))
        assertNull(circularMeanWindDirection(listOf(0.0, 180.0)))
    }

    @Test
    fun compassKeepsDegreeAndLabelConsistentForReferenceAngles() {
        val previous = I18n.lang
        I18n.lang = AppLanguage.English
        try {
            listOf(0.0 to "N", 90.0 to "E", 180.0 to "S", 270.0 to "W", 330.0 to "NNW").forEach { (degrees, expected) ->
                assertEquals(expected, windDirectionLabel(degrees))
            }
        } finally {
            I18n.lang = previous
        }
    }
}
