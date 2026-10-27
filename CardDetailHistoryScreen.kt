package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catsblock.weather.R
import com.catsblock.weather.atmosphere.WeatherHistoryRecord
import com.catsblock.weather.atmosphere.WeatherMetric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class DailyMetricSummary(
    val date: LocalDate,
    val average: Double?,
    val peak: Double?,
    val sampleCount: Int
)

@Composable
fun CardDetailHistoryScreen(
    metric: WeatherMetric,
    records: List<WeatherHistoryRecord>,
    initialDate: LocalDate,
    onBack: () -> Unit
) {
    var selectedDate by remember(metric, initialDate) { mutableStateOf(initialDate) }
    val today = initialDate
    val week = remember(metric, records, selectedDate) {
        (-6..0).map { offset ->
            val date = selectedDate.plusDays(offset.toLong())
            val values = records.filter { it.timestamp.toLocalDate() == date }.map { it.value(metric) }
            DailyMetricSummary(
                date = date,
                average = values.takeIf { it.isNotEmpty() }?.average(),
                peak = values.maxOrNull(),
                sampleCount = values.size
            )
        }
    }
    val selectedRecords = remember(metric, records, selectedDate) {
        records.filter { it.timestamp.toLocalDate() == selectedDate }
    }
    val selectedPeak = selectedRecords.maxByOrNull { it.value(metric) }
    val dateText = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }
    val availableValues = week.mapNotNull { it.average }
    val minValue = availableValues.minOrNull() ?: 0.0
    val maxValue = availableValues.maxOrNull() ?: 1.0
    val span = (maxValue - minValue).takeIf { it > 0.0001 } ?: 1.0

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(metric.titleRes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.history_screen_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.previous_day))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dateText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.history_observation_count, selectedRecords.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { if (selectedDate < today) selectedDate = selectedDate.plusDays(1) }, enabled = selectedDate < today) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.next_day))
                }
            }
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(stringResource(R.string.history_weekly_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(164.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    week.forEach { day ->
                        key(day.date) {
                            val normalized = day.average?.let { ((it - minValue) / span).toFloat().coerceIn(0f, 1f) } ?: 0f
                            val barHeight = if (day.average == null) 8.dp else (30f + normalized * 92f).dp
                            Column(
                                modifier = Modifier.weight(1f).height(164.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (day.peak != null) {
                                    Text(
                                        formatMetric(day.peak, metric),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(5.dp))
                                } else {
                                    Spacer(Modifier.height(18.dp))
                                }
                                Box(
                                    Modifier
                                        .width(20.dp)
                                        .height(barHeight)
                                        .background(
                                            if (day.date == selectedDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(50)
                                        )
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    day.date.format(DateTimeFormatter.ofPattern("EE", Locale.getDefault())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (day.date == selectedDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (selectedRecords.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.history_no_observations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(stringResource(R.string.history_peak_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        selectedPeak?.let { formatPrimaryValue(it.value(metric), metric) } ?: stringResource(R.string.history_no_value),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(metric.unitRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    selectedPeak?.timestamp?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
                        ?.let { stringResource(R.string.history_peak_time, it) }
                        ?: stringResource(R.string.history_peak_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (metric == WeatherMetric.CloudCover && selectedPeak?.cloudCoverEstimated == true) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.history_estimated_data), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Column {
                    Text(stringResource(R.string.history_technical_guidance), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(metric.guidanceRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun formatMetric(value: Double, metric: WeatherMetric): String = when (metric) {
    WeatherMetric.CloudCover, WeatherMetric.Humidity -> "${value.roundToInt()}${stringResource(R.string.percent_symbol)}"
    WeatherMetric.Pressure -> value.roundToInt().toString()
    WeatherMetric.Temperature -> value.roundToInt().toString()
    WeatherMetric.Wind -> value.roundToInt().toString()
    WeatherMetric.Precipitation -> "%.1f".format(Locale.getDefault(), value)
    WeatherMetric.UvIndex -> "%.1f".format(Locale.getDefault(), value)
}

private fun formatPrimaryValue(value: Double, metric: WeatherMetric): String = when (metric) {
    WeatherMetric.CloudCover, WeatherMetric.Humidity, WeatherMetric.Pressure, WeatherMetric.Temperature, WeatherMetric.Wind -> value.roundToInt().toString()
    WeatherMetric.Precipitation, WeatherMetric.UvIndex -> "%.1f".format(Locale.getDefault(), value)
}
