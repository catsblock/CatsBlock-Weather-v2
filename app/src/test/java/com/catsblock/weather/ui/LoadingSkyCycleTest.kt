package com.catsblock.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LoadingSkyCycleTest {
    private val date = LocalDate.of(2026, 11, 16)
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val start = 8_000_000_000L

    @Test
    fun startsAtFiveAndCompletesDayInSixtySeconds() {
        assertEquals(LocalTime.of(5, 0), loadingSkyTime(start, start, date, zone).toLocalTime())
        assertEquals(LocalTime.of(17, 0), loadingSkyTime(start, start + 30_000_000_000L, date, zone).toLocalTime())
        assertEquals(LocalTime.of(5, 0), loadingSkyTime(start, start + LOADING_SKY_CYCLE_NANOS, date, zone).toLocalTime())
        assertEquals(date.plusDays(1), loadingSkyTime(start, start + LOADING_SKY_CYCLE_NANOS, date, zone).toLocalDate())
    }

    @Test
    fun cycleIsMonotonicAndRepeatsTheDayClockOnTheNextCalendarDay() {
        val earlier = loadingSkyTime(start, start + 10_000_000_000L, date, zone).toInstant()
        val later = loadingSkyTime(start, start + 20_000_000_000L, date, zone).toInstant()
        assertEquals(true, later.isAfter(earlier))
        val firstCycle = loadingSkyTime(start, start + 1_000_000L, date, zone)
        val nextCycle = loadingSkyTime(start, start + LOADING_SKY_CYCLE_NANOS + 1_000_000L, date, zone)
        assertEquals(firstCycle.toLocalTime(), nextCycle.toLocalTime())
        assertEquals(date.plusDays(1), nextCycle.toLocalDate())
        assertEquals(true, nextCycle.toInstant().isAfter(firstCycle.toInstant()))
    }

    @Test
    fun clockCanBeInjectedAsPureInput() {
        var fakeNow = start
        val atStart = loadingSkyTimeAt({ fakeNow }, start, date, zone)
        fakeNow += LOADING_SKY_CYCLE_NANOS / 4
        val oneQuarter = loadingSkyTimeAt({ fakeNow }, start, date, zone)
        assertEquals(LocalTime.of(5, 0), atStart.toLocalTime())
        assertEquals(LocalTime.of(11, 0), oneQuarter.toLocalTime())
    }
}
