package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.Day
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.aqiStatus
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.humidityLabel
import com.catsblock.weather.data.lifeIndex
import com.catsblock.weather.data.precipLabel
import com.catsblock.weather.data.uvLevel
import com.catsblock.weather.i18n.tr
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    day: Day,
    hours: List<Hour>,
    fahrenheit: Boolean,
    today: LocalDate,
    now: LocalDateTime,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dayHours = remember(day, hours) { hours.filter { it.time.toLocalDate() == day.date } }
    val chartScroll = rememberScrollState()
    val night = MaterialTheme.colorScheme.copy(
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.72f),
        surfaceContainer = Color(0xFF1B2740),
        surfaceContainerHigh = Color(0xFF24324F),
        primary = Color(0xFF8EC5FF),
        secondary = Color(0xFF8EC5FF),
        tertiary = Color(0xFF8EC5FF),
        secondaryContainer = Color.White.copy(alpha = 0.20f),
        onSecondaryContainer = Color.White,
        outlineVariant = Color.White.copy(alpha = 0.20f)
    )

    MaterialTheme(colorScheme = night) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color(0xFF0B1220),
            contentColor = Color.White
        ) {
            Box(Modifier.fillMaxSize()) {
                WeatherBackground(day.kind, true, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(longDateLabel(day.date), style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(
                                "${day.kind.label(true)}  ·  ${tempText(day.max, fahrenheit)} / ${tempText(day.min, fahrenheit)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                        WeatherIcon(day.kind, true, Modifier.size(64.dp))
                    }

                    if (dayHours.isEmpty()) {
                        Text(
                            tr("No hourly data for this day."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    } else {
                        val windPeak = dayHours.maxBy { it.windSpeed }
                        val aqiMax = dayHours.mapNotNull { it.aqi }.maxOrNull()
                        val humidity = dayHours.map { it.humidity }.average().roundToInt()
                        val avgTemp = dayHours.map { it.temp }.average()
                        val avgFeels = dayHours.map { it.feels }.average()
                        val sunSpan = Duration.between(day.sunrise, day.sunset).toMinutes()
                        val sunNow = if (day.date == today) now else day.sunrise.plusMinutes(sunSpan / 2)

                        HoursCard(
                            hours = dayHours,
                            days = listOf(day),
                            today = today,
                            fahrenheit = fahrenheit,
                            scrollState = chartScroll,
                            title = tr("24 hours"),
                            showDays = false
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                title = tr("Precipitation"),
                                icon = Icons.Rounded.Umbrella,
                                value = f1(day.precipSum),
                                unit = "mm",
                                description = if (day.precipSum <= 0.0) tr("No precipitation expected.")
                                else "${day.rainChance}% · ${precipLabel(day.precipSum)}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = tr("Wind"),
                                icon = Icons.Rounded.Air,
                                value = beaufort(windPeak.windSpeed).toString(),
                                unit = "${compassLabel(windPeak.windDir)} | ${windText(windPeak.windSpeed, LocalUnits.current.wind)}",
                                description = windShort(windPeak.windSpeed),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                title = tr("Air quality"),
                                icon = Icons.Rounded.Eco,
                                value = aqiMax?.toInt()?.toString() ?: "--",
                                unit = aqiMax?.let { aqiStatus(it) },
                                description = aqiMax?.let { aqiShort(it) } ?: tr("Air quality data is not available here."),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = tr("UV index"),
                                icon = Icons.Rounded.WbSunny,
                                value = day.uvMax.toInt().toString(),
                                unit = uvLevel(day.uvMax),
                                description = uvShort(day.uvMax),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                title = tr("Humidity"),
                                icon = Icons.Rounded.WaterDrop,
                                value = "$humidity%",
                                unit = null,
                                description = humidityLabel(humidity),
                                modifier = Modifier.weight(1f)
                            )
                            SunCard(day.sunrise, day.sunset, sunNow, Modifier.weight(1f))
                        }

                        GlassCard(Modifier.fillMaxWidth()) {
                            CardHeader(Icons.Rounded.Umbrella, tr("Hourly precipitation"))
                            Spacer(Modifier.height(8.dp))
                            SimpleChart(
                                values = dayHours.map { it.precip },
                                labels = dayHours.map { "${hourLabel(it.time)}\n${it.kind.label(it.isDay)}" },
                                valueLabel = { f1(it) },
                                bar = true,
                                minY = 0.0,
                                maxY = dayHours.maxOf { it.precip }.coerceAtLeast(1.0),
                                color = MaterialTheme.colorScheme.tertiary,
                                stepWidth = 72.dp
                            )
                        }

                        if (aqiMax != null) {
                            val aqiHours = dayHours.filter { it.aqi != null }
                            GlassCard(Modifier.fillMaxWidth()) {
                                CardHeader(Icons.Rounded.Eco, tr("Hourly air quality"))
                                Spacer(Modifier.height(8.dp))
                                SimpleChart(
                                    values = aqiHours.map { it.aqi ?: 0.0 },
                                    labels = aqiHours.map { "${hourLabel(it.time)}\n${aqiStatus(it.aqi ?: 0.0)}" },
                                    valueLabel = { it.toInt().toString() },
                                    minY = 0.0,
                                    maxY = aqiMax.coerceAtLeast(50.0),
                                    stepWidth = 72.dp
                                )
                            }
                        }

                        LifeIndexCard(
                            lifeIndex(
                                temp = avgTemp,
                                feels = avgFeels,
                                humidity = humidity,
                                uv = day.uvMax,
                                aqi = aqiMax,
                                wind = windPeak.windSpeed,
                                precip = day.precipSum,
                                kind = day.kind,
                                dayMax = day.max,
                                dayMin = day.min
                            )
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
