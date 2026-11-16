package com.catsblock.weather.ui

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

const val LOADING_SKY_CYCLE_NANOS: Long = 60_000_000_000L
private const val DAY_NANOS = 86_400_000_000_000L

fun loadingSkyTime(startNanos: Long, nowNanos: Long, date: LocalDate, zone: ZoneId): ZonedDateTime {
    val elapsed = (nowNanos - startNanos).coerceAtLeast(0L)
    val completedCycles = elapsed / LOADING_SKY_CYCLE_NANOS
    val cycleElapsed = elapsed % LOADING_SKY_CYCLE_NANOS
    val dayNanos = (cycleElapsed.toDouble() / LOADING_SKY_CYCLE_NANOS * DAY_NANOS).toLong()
    val localTime = date.plusDays(completedCycles).atTime(LocalTime.of(5, 0)).plus(Duration.ofNanos(dayNanos))
    return localTime.atZone(zone)
}

fun loadingSkyTimeAt(nowNanos: () -> Long, startNanos: Long, date: LocalDate, zone: ZoneId): ZonedDateTime =
    loadingSkyTime(startNanos, nowNanos(), date, zone)

fun loadingSkyEpochMillis(time: ZonedDateTime): Long = time.toInstant().toEpochMilli()
