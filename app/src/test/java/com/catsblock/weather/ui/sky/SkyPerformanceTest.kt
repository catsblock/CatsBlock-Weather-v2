package com.catsblock.weather.ui.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyPerformanceTest {
    @Test
    fun classifiesStablePresentationRatesIntoQualityBands() {
        assertEquals(SkyTier.Ultra, SkyPerformance.classify(metrics(16_000_000L)))
        assertEquals(SkyTier.High, SkyPerformance.classify(metrics(20_000_000L)))
        assertEquals(SkyTier.Medium, SkyPerformance.classify(metrics(30_000_000L)))
        assertEquals(SkyTier.VeryLow, SkyPerformance.classify(metrics(50_000_000L)))
    }

    @Test
    fun variabilityAndDroppedFramesAreMeasuredAndThrottleClassification() {
        val values = List(20) { if (it % 2 == 0) 16_000_000L else 50_000_000L }
        val result = SkyPerformance.metrics(values)
        assertTrue(result.variability > 0.4)
        assertTrue(result.droppedFraction > 0.4)
        assertTrue(SkyPerformance.isConsistentlySlow(result))
        assertTrue(SkyPerformance.classify(result) != SkyTier.Ultra)
    }

    @Test
    fun cpuAndGpuTailLatencyPreventUltraClassification() {
        val intervals = LongArray(100) { 16_666_667L }
        val total = LongArray(100) { 8_000_000L }
        val gpu = LongArray(100) { 4_000_000L }
        for (i in 90 until 100) {
            total[i] = 30_000_000L
            gpu[i] = 20_000_000L
        }
        val metric = SkyPerformance.metrics(intervals, intervals.size, totalDurationsNanos = total, gpuDurationsNanos = gpu)
        assertEquals(30_000_000L, metric.p95TotalDurationNanos)
        assertEquals(20_000_000L, metric.p95GpuDurationNanos)
        assertEquals(SkyTier.High, SkyPerformance.classify(metric))
    }

    @Test
    fun collectorUsesAnInitialProbeWindowThenContinuousSamples() {
        val collector = SkyFrameMetricsCollector(
            expectedIntervalNanos = 16_666_667L,
            initialWindowNanos = 250_000_000L,
            sampleWindowNanos = 500_000_000L
        )
        val results = mutableListOf<SkyFrameMetrics>()
        for (frame in 0..100) {
            val now = frame * 16_666_667L
            collector.record(now, totalDurationNanos = 8_000_000L, gpuDurationNanos = 4_000_000L)?.let(results::add)
        }
        assertEquals(3, results.size)
        assertTrue(results.first().sampleCount >= 30)
        assertTrue(results.drop(1).all { it.sampleCount >= 30 })
    }

    @Test
    fun qualityRecoveryRequiresConfirmationAndAdvancesOnlyOneTier() {
        val metric = metrics(16_000_000L)
        assertEquals(SkyTier.Medium, SkyPerformance.nextTier(SkyTier.Medium, SkyTier.Ultra, metric, recoveryConfirmed = false))
        assertEquals(SkyTier.High, SkyPerformance.nextTier(SkyTier.Medium, SkyTier.Ultra, metric, recoveryConfirmed = true))
    }

    @Test
    fun renderingCadenceUsesStableSubmultiplesOfDisplayRefresh() {
        assertEquals(60, SkyTier.Ultra.targetFps(60f))
        assertEquals(45, SkyTier.High.targetFps(90f))
        assertEquals(30, SkyTier.Medium.targetFps(120f))
        assertEquals(20, SkyTier.Low.targetFps(60f))
        assertEquals(15, SkyTier.VeryLow.targetFps(60f))
    }

    private fun metrics(interval: Long): SkyFrameMetrics = SkyPerformance.metrics(List(60) { interval })
}
