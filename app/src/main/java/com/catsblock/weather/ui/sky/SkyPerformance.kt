package com.catsblock.weather.ui.sky

import java.util.Arrays
import kotlin.math.ceil
import kotlin.math.sqrt

internal data class SkyFrameMetrics(
    val averageFps: Double,
    val variability: Double,
    val droppedFraction: Double,
    val sampleCount: Int,
    val medianIntervalNanos: Long,
    val p95IntervalNanos: Long,
    val p99IntervalNanos: Long,
    val p95TotalDurationNanos: Long,
    val p95GpuDurationNanos: Long,
    val expectedIntervalNanos: Long
)

internal class SkyFrameMetricsCollector(
    private val expectedIntervalNanos: Long,
    private val initialWindowNanos: Long = 3_000_000_000L,
    private val sampleWindowNanos: Long = 10_000_000_000L,
    private val capacity: Int = 4096
) {
    private val intervals = LongArray(capacity)
    private val totalDurations = LongArray(capacity)
    private val gpuDurations = LongArray(capacity)
    private var previousVsyncNanos = 0L
    private var windowStartNanos = 0L
    private var count = 0
    private var firstWindow = true

    fun reset() {
        previousVsyncNanos = 0L
        windowStartNanos = 0L
        count = 0
        firstWindow = true
    }

    fun record(vsyncNanos: Long, totalDurationNanos: Long, gpuDurationNanos: Long): SkyFrameMetrics? {
        if (vsyncNanos <= 0L) return null
        if (previousVsyncNanos == 0L) {
            previousVsyncNanos = vsyncNanos
            windowStartNanos = vsyncNanos
            return null
        }
        val interval = vsyncNanos - previousVsyncNanos
        previousVsyncNanos = vsyncNanos
        if (interval <= 0L || interval > 250_000_000L) {
            reset()
            previousVsyncNanos = vsyncNanos
            windowStartNanos = vsyncNanos
            return null
        }
        if (count == capacity) {
            count = 0
            windowStartNanos = vsyncNanos
            firstWindow = false
            return null
        }
        intervals[count] = interval
        totalDurations[count] = totalDurationNanos
        gpuDurations[count] = gpuDurationNanos
        count++
        val windowLength = if (firstWindow) initialWindowNanos else sampleWindowNanos
        if (vsyncNanos - windowStartNanos < windowLength || count < 30) return null

        val result = SkyPerformance.metrics(
            intervals,
            count,
            expectedIntervalNanos,
            totalDurations,
            gpuDurations
        )
        firstWindow = false
        count = 0
        windowStartNanos = vsyncNanos
        return result
    }
}

internal object SkyPerformance {
    fun metrics(intervalsNanos: List<Long>, expectedIntervalNanos: Long = 16_666_667L): SkyFrameMetrics =
        metrics(intervalsNanos.toLongArray(), intervalsNanos.size, expectedIntervalNanos)

    fun metrics(
        intervalsNanos: LongArray,
        count: Int,
        expectedIntervalNanos: Long = 16_666_667L,
        totalDurationsNanos: LongArray? = null,
        gpuDurationsNanos: LongArray? = null
    ): SkyFrameMetrics {
        val validCount = count.coerceIn(0, minOf(intervalsNanos.size, 4096))
        val valid = intervalsNanos.copyOf(validCount).filter { it > 0L }
        if (valid.isEmpty()) {
            return SkyFrameMetrics(0.0, 1.0, 1.0, 0, 0L, 0L, 0L, 0L, 0L, expectedIntervalNanos)
        }
        val mean = valid.average()
        val variance = valid.sumOf { (it - mean) * (it - mean) } / valid.size
        val medianInterval = percentile(valid, 0.50)
        val drops = valid.count { it > medianInterval * 1.5 }.toDouble() / valid.size
        val work = totalDurationsNanos?.take(validCount)?.filter { it > 0L }.orEmpty()
        val gpu = gpuDurationsNanos?.take(validCount)?.filter { it > 0L }.orEmpty()
        return SkyFrameMetrics(
            averageFps = 1e9 / mean,
            variability = sqrt(variance) / mean,
            droppedFraction = drops,
            sampleCount = valid.size,
            medianIntervalNanos = medianInterval,
            p95IntervalNanos = percentile(valid, 0.95),
            p99IntervalNanos = percentile(valid, 0.99),
            p95TotalDurationNanos = percentile(work, 0.95),
            p95GpuDurationNanos = percentile(gpu, 0.95),
            expectedIntervalNanos = expectedIntervalNanos
        )
    }

    fun classify(metrics: SkyFrameMetrics): SkyTier {
        if (metrics.sampleCount < 30) return SkyTier.Medium
        val pacingStable = metrics.variability <= 0.22 &&
            metrics.p95IntervalNanos <= maxOf(
                metrics.expectedIntervalNanos * 2L,
                (metrics.medianIntervalNanos * 1.55).toLong()
            )
        val workloadOverBudget =
            (metrics.p95TotalDurationNanos > 0L && metrics.p95TotalDurationNanos > metrics.expectedIntervalNanos * 1.2) ||
                (metrics.p95GpuDurationNanos > 0L && metrics.p95GpuDurationNanos > metrics.expectedIntervalNanos * 0.8)
        return when {
            metrics.averageFps >= 55.0 && pacingStable && metrics.droppedFraction <= 0.08 && !workloadOverBudget -> SkyTier.Ultra
            metrics.averageFps >= 55.0 -> SkyTier.High
            metrics.averageFps >= 40.0 && pacingStable && metrics.droppedFraction <= 0.15 && !workloadOverBudget -> SkyTier.High
            metrics.averageFps >= 40.0 -> SkyTier.Medium
            metrics.averageFps >= 25.0 && pacingStable -> SkyTier.Medium
            metrics.averageFps >= 25.0 -> SkyTier.Low
            else -> SkyTier.VeryLow
        }
    }

    fun nextTier(
        current: SkyTier,
        target: SkyTier,
        metrics: SkyFrameMetrics,
        recoveryConfirmed: Boolean = true
    ): SkyTier {
        if (metrics.sampleCount < 30) return current
        if (target.ordinal < current.ordinal) return current.lowered()
        if (target.ordinal > current.ordinal) {
            if (!recoveryConfirmed || !isStableForRecovery(metrics)) return current
            return SkyTier.entries[(current.ordinal + 1).coerceAtMost(target.ordinal)]
        }
        return current
    }

    fun isStableForRecovery(metrics: SkyFrameMetrics): Boolean =
        metrics.sampleCount >= 30 &&
            metrics.variability <= 0.18 &&
            metrics.droppedFraction <= 0.12 &&
            metrics.p95IntervalNanos <= maxOf(
                metrics.expectedIntervalNanos * 2L,
                (metrics.medianIntervalNanos * 1.5).toLong()
            )

    fun shouldSampleAgain(metrics: SkyFrameMetrics, elapsedNanos: Long): Boolean =
        elapsedNanos >= 3_000_000_000L && metrics.sampleCount >= 30

    fun isConsistentlySlow(metrics: SkyFrameMetrics): Boolean =
        metrics.averageFps < 25.0 ||
            (metrics.averageFps < 40.0 && (metrics.variability > 0.35 || metrics.droppedFraction > 0.18))

    private fun percentile(values: List<Long>, fraction: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.toLongArray()
        Arrays.sort(sorted)
        val index = (ceil(fraction * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
