package com.catsblock.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.RadarReading
import com.catsblock.weather.data.StormIntensity
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.WindUnit
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.isWet
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val STATIONARY_KMH = 3.0
private const val RADAR_HOURS = 6L
private const val MODEL_HOURS = 36L
private const val PRESSURE_STEP = 1.0

@Composable
fun StormTrackerCard(
    radar: RadarReading,
    bundle: WeatherBundle,
    windUnit: WindUnit,
    modifier: Modifier = Modifier
) {
    val locale = I18n.lang.locale
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val now = bundle.current.now
    val track = radar.track
    val frameTime = if (radar.frameEpoch > 0L) {
        LocalDateTime.ofEpochSecond(radar.frameEpoch, 0, ZoneOffset.ofTotalSeconds(bundle.utcOffsetSeconds)).format(formatter)
    } else {
        null
    }
    GlassCard(modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Radar storm tracker"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (frameTime != null) {
                    Text(frameTime, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (radar.wet) tr("Raining at your location") else tr("No rain at your location"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (track == null) {
                Text(tr("No storm echoes within range"), style = MaterialTheme.typography.bodySmall)
            } else {
                val speed = track.speedKmh
                val bearing = track.bearingDeg
                if (speed != null && bearing != null) {
                    StormRow(
                        tr("Moving toward"),
                        if (speed < STATIONARY_KMH) tr("Stationary") else "${compassLabel(bearing)} (${bearing.roundToInt()}°)"
                    )
                    StormRow(
                        tr("Speed"),
                        String.format(locale, "%.0f %s", speed * windUnit.factor, windUnit.label)
                    )
                }
                StormRow(
                    tr("Distance"),
                    if (track.edgeDistanceKm <= 0.0) tr("Now") else String.format(locale, "%.0f km", track.edgeDistanceKm)
                )
                StormRow(tr("Reflectivity"), String.format(locale, "%.0f dBZ", track.peakDbz))
                StormRow(
                    tr("Rain rate"),
                    String.format(locale, "%.1f mm/h", track.rateMmh) + " · " + intensityLabel(track.intensity)
                )
                val eta = track.etaMinutes
                StormRow(
                    tr("Estimated arrival"),
                    when {
                        !track.onTarget || eta == null -> tr("Not heading your way")
                        eta <= 0 -> tr("Now")
                        else -> "$eta ${tr("min")} (${now.plusMinutes(eta.toLong()).format(formatter)})"
                    }
                )
                val duration = track.durationMinutes
                if (track.onTarget && duration != null && duration > 0) {
                    StormRow(tr("Expected duration"), "$duration ${tr("min")}")
                }
            }
            Spacer(Modifier.height(12.dp))
            StormRow(tr("Next 6 hours (radar)"), nearOutlook(radar, bundle, formatter))
            val far = farOutlook(bundle, formatter)
            StormRow(tr("6 to 36 hours (models)"), far.first)
            far.second?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun StormRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
    }
}

private fun intensityLabel(intensity: StormIntensity): String = when (intensity) {
    StormIntensity.Light -> tr("Light")
    StormIntensity.Moderate -> tr("Moderate")
    StormIntensity.Severe -> tr("Severe")
    StormIntensity.Extreme -> tr("Extreme")
}

private fun nearOutlook(radar: RadarReading, bundle: WeatherBundle, formatter: DateTimeFormatter): String {
    val now = bundle.current.now
    val track = radar.track
    if (radar.wet) return tr("Raining at your location")
    if (track != null && track.onTarget) {
        val eta = track.etaMinutes ?: 0
        return if (eta <= 0) {
            tr("Raining at your location")
        } else {
            tr("Rain starts around") + " " + now.plusMinutes(eta.toLong()).format(formatter)
        }
    }
    val start = now.truncatedTo(ChronoUnit.HOURS)
    val wet = bundle.hours.firstOrNull { !it.time.isBefore(start) && it.time.isBefore(now.plusHours(RADAR_HOURS)) && it.kind.isWet }
    return if (wet != null) tr("Rain possible around") + " " + wet.time.format(formatter) else tr("No rain expected")
}

private fun farOutlook(bundle: WeatherBundle, formatter: DateTimeFormatter): Pair<String, String?> {
    val now = bundle.current.now
    val from = now.plusHours(RADAR_HOURS)
    val to = now.plusHours(MODEL_HOURS)
    val window = bundle.hours.filter { !it.time.isBefore(from) && it.time.isBefore(to) }
    val wet = window.filter { it.kind.isWet }
    val summary = if (wet.isEmpty()) {
        tr("No rain expected")
    } else {
        tr("Rain possible around") + " " + wet.first().time.format(formatter) + " · " + tr("Rain hours") + " " + wet.size
    }
    if (window.size < 2) return summary to null
    val delta = window.last().pressure - window.first().pressure
    val trend = when {
        delta <= -PRESSURE_STEP -> tr("Falling")
        delta >= PRESSURE_STEP -> tr("Rising")
        else -> tr("Steady")
    }
    val humidity = window.map { it.humidity }.average().roundToInt()
    val day = window.filter { it.isDay }
    val detail = StringBuilder(tr("Pressure") + " " + trend + " · " + tr("Humidity") + " " + humidity + "%")
    if (day.isNotEmpty()) {
        detail.append(" · ").append(tr("UV index")).append(" ").append(day.map { it.uv }.average().roundToInt())
    }
    return summary to detail.toString()
}
