package com.catsblock.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecipitationChartInterpolationTest {
    @Test
    fun zeroRemainsZeroAndNeverGoesNegative() {
        val curve = monotoneChartSegments(listOf(ChartSample(0, 0.0), ChartSample(10, 0.0), ChartSample(20, 3.0)))
        assertEquals(0.0, curve.first().first().value, 0.0)
        assertTrue(curve.flatten().all { it.value >= 0.0 })
    }

    @Test
    fun monotoneRiseAndFallDoNotOvershoot() {
        val rising = monotoneChartSegments(listOf(ChartSample(0, 1.0), ChartSample(10, 2.0), ChartSample(20, 5.0))).single()
        assertTrue(rising.zipWithNext().all { (a, b) -> b.value >= a.value })
        assertTrue(rising.all { it.value in 1.0..5.0 })
        val falling = monotoneChartSegments(listOf(ChartSample(0, 5.0), ChartSample(10, 2.0), ChartSample(20, 1.0))).single()
        assertTrue(falling.zipWithNext().all { (a, b) -> b.value <= a.value })
        assertTrue(falling.all { it.value in 1.0..5.0 })
    }

    @Test
    fun abruptPeakIsPreservedAndDoesNotCreateOvershoot() {
        val curve = monotoneChartSegments(listOf(ChartSample(0, 0.0), ChartSample(10, 9.0), ChartSample(20, 0.0))).single()
        assertEquals(9.0, curve.maxOf { it.value }, 0.0)
        assertTrue(curve.all { it.value in 0.0..9.0 })
        assertEquals(9.0, curve.first { it.timestamp == 10L }.value, 0.0)
    }

    @Test
    fun missingSamplesBreakCurveWithoutInventingValues() {
        val segments = monotoneChartSegments(listOf(ChartSample(0, 1.0), ChartSample(10, null), ChartSample(20, 4.0)))
        assertEquals(2, segments.size)
        assertEquals(listOf(1.0, 4.0), segments.flatten().map { it.value })
    }

    @Test
    fun irregularTimestampsAreRetainedAndUsedForInterpolation() {
        val curve = monotoneChartSegments(listOf(ChartSample(100, 0.0), ChartSample(130, 3.0), ChartSample(500, 4.0))).single()
        assertEquals(100L, curve.first().timestamp)
        assertEquals(500L, curve.last().timestamp)
        assertTrue(curve.any { it.timestamp == 130L && it.value == 3.0 })
        val afterFirst = curve.first { it.timestamp > 100L }
        assertTrue(afterFirst.timestamp <= 130L)
    }

    @Test
    fun unorderedAndDuplicateTimesAreNormalizedWithoutNonFiniteValues() {
        val curve = monotoneChartSegments(
            listOf(ChartSample(300, 6.0), ChartSample(100, 1.0), ChartSample(100, 2.0), ChartSample(200, Double.NaN))
        ).single()
        assertEquals(listOf(100L, 300L), listOf(curve.first().timestamp, curve.last().timestamp))
        assertEquals(2.0, curve.first().value, 0.0)
        assertTrue(curve.all { it.value.isFinite() })
    }

    @Test
    fun changedSeriesInterpolatesAtActualTimestampInsteadOfArrayIndex() {
        val previous = listOf(ChartSample(0, 0.0), ChartSample(100, 10.0))
        val next = listOf(ChartSample(50, 10.0), ChartSample(100, 0.0))
        val halfway = interpolateChartSamples(previous, next, 0.5f)
        assertEquals(7.5, halfway[0].value!!, 0.0001)
        assertEquals(5.0, halfway[1].value!!, 0.0001)
    }

    @Test
    fun newSamplesOutsidePreviousRangeDoNotAnimateFromInventedZero() {
        val previous = listOf(ChartSample(0, 2.0), ChartSample(100, 4.0))
        val next = listOf(ChartSample(200, 8.0))
        val initial = interpolateChartSamples(previous, next, 0f)
        assertEquals(8.0, initial.single().value!!, 0.0)
    }
}
