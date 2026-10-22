package com.catsblock.weather.data

import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.trx
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AlertKind { LightRain, LightSnow, HeavyRain, HeavySnow, Fog, Warm, Cool }

data class WeatherAlert(val kind: AlertKind, val title: String)

private val snowCodes = setOf(71, 73, 75, 77, 85, 86)
private val lightCodes = setOf(51, 53, 55, 56, 57, 61, 66, 71, 77, 80, 85)
private val heavyCodes = setOf(63, 65, 67, 73, 75, 81, 82, 86, 95, 96, 99)
private val fogCodes = setOf(45, 48)

private fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

private fun hoursText(n: Int, duration: Boolean): String {
    if (I18n.lang == AppLanguage.Russian) {
        val word = if (duration) {
            if (n % 10 == 1 && n % 100 != 11) "часа" else "часов"
        } else {
            ruPlural(n, "час", "часа", "часов")
        }
        return "$n $word"
    }
    return if (n == 1) trx("1 hour") else trx("%d hours").format(n)
}

private fun daysText(n: Int): String {
    if (I18n.lang == AppLanguage.Russian) return "$n " + ruPlural(n, "день", "дня", "дней")
    return if (n == 1) trx("1 day") else trx("%d days").format(n)
}

fun buildAlert(bundle: WeatherBundle, fahrenheit: Boolean): WeatherAlert? {
    val nowHour = bundle.current.now.truncatedTo(ChronoUnit.HOURS)
    val start = bundle.hours.indexOfFirst { !it.time.isBefore(nowHour) }.coerceAtLeast(0)
    val ahead = bundle.hours.drop(start).take(24)
    val soon = ahead.take(8)

    val heavyAt = soon.indexOfFirst { it.code in heavyCodes }
    if (heavyAt >= 0) {
        val snow = soon[heavyAt].code in snowCodes
        val template = if (snow) {
            "A moderate, heavy snow is arriving here in %s, be alert in case it snows."
        } else {
            "A moderate, heavy rain is arriving here in %s, be alert in case it rains."
        }
        return WeatherAlert(
            if (snow) AlertKind.HeavySnow else AlertKind.HeavyRain,
            trx(template).format(hoursText(heavyAt.coerceAtLeast(1), false))
        )
    }

    val lightAt = soon.indexOfFirst { it.code in lightCodes }
    if (lightAt >= 0) {
        val snow = soon[lightAt].code in snowCodes
        var count = 0
        while (lightAt + count < ahead.size && ahead[lightAt + count].code in lightCodes) count++
        val template = if (snow) {
            "A light snow is expected here for %s. Be concerned if it snows."
        } else {
            "A light rain is expected here for %s. Be concerned if it rains."
        }
        return WeatherAlert(
            if (snow) AlertKind.LightSnow else AlertKind.LightRain,
            trx(template).format(hoursText(count, true))
        )
    }

    val fogAt = ahead.take(12).indexOfFirst { it.code in fogCodes }
    if (fogAt >= 0) {
        var count = 0
        while (fogAt + count < ahead.size && ahead[fogAt + count].code in fogCodes) count++
        if (count <= 4) {
            return WeatherAlert(
                AlertKind.Fog,
                trx("The city may be shrouded in fog for %s. Do not travel when there is fog.")
                    .format(hoursText(count, true))
            )
        }
    }

    val today = bundle.current.now.toLocalDate()
    val upcoming = bundle.days.filter { !it.date.isBefore(today) }
    if (upcoming.size < 2) return null
    val base = upcoming[0].max
    for (i in 1 until minOf(upcoming.size, 7)) {
        val delta = upcoming[i].max - base
        if (abs(delta) >= 4.0) {
            val shown = (abs(delta) * (if (fahrenheit) 1.8 else 1.0)).roundToInt()
            return if (delta > 0) {
                WeatherAlert(
                    AlertKind.Warm,
                    trx("The temperature will increase by approximately %1\$s° in %2\$s.")
                        .format(shown.toString(), daysText(i))
                )
            } else {
                WeatherAlert(
                    AlertKind.Cool,
                    trx("The temperature will decrease by %1\$s° in %2\$s.")
                        .format(shown.toString(), daysText(i))
                )
            }
        }
    }
    return null
}
