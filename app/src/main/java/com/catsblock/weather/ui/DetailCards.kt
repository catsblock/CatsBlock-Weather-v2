package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.Day
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.LifeItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
    val shape = MaterialTheme.shapes.extraLarge
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
}

@Composable
fun MetricCard(
    title: String,
    icon: ImageVector,
    value: String,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    GlassCard(modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HourlyListCard(hours: List<Hour>, fahrenheit: Boolean, onHourClick: (Int) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("Next 36 hours")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            hours.forEachIndexed { i, h ->
                Column(
                    Modifier
                        .width(72.dp)
                        .clickable { onHourClick(i) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (i == 0) "Now" else hourLabel(h.time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WeatherIcon(h.kind, h.isDay, Modifier.size(40.dp))
                    Text(tempText(h.temp, fahrenheit), style = MaterialTheme.typography.titleSmall)
                    Text(
                        h.kind.label(h.isDay),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyChartCard(hours: List<Hour>, fahrenheit: Boolean, scrollState: ScrollState) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("36-hour temperature")
        HourlyTempChart(hours, fahrenheit, scrollState)
    }
}

@Composable
private fun HourlyTempChart(hours: List<Hour>, fahrenheit: Boolean, scrollState: ScrollState) {
    val step = 64.dp
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.horizontalScroll(scrollState)) {
        Row {
            hours.forEach { h ->
                Column(Modifier.width(step), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherIcon(h.kind, h.isDay, Modifier.size(32.dp))
                    Text(
                        h.kind.label(h.isDay),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Canvas(Modifier.width(step * hours.size).height(170.dp)) {
            val topPad = 24.dp.toPx()
            val bottomPad = 28.dp.toPx()
            val plotH = size.height - topPad - bottomPad
            val stepPx = size.width / hours.size
            val baseline = topPad + plotH

            fun yOf(c: Double): Float = topPad + (1f - (c.coerceIn(0.0, 35.0) / 35.0).toFloat()) * plotH

            drawLine(grid, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
            drawLine(grid, Offset(0f, topPad), Offset(size.width, topPad), 1.dp.toPx())
            val top = measurer.measure("${35.0.toUnit(fahrenheit).toInt()}°", labelStyle)
            drawText(top, topLeft = Offset(2.dp.toPx(), topPad + 2.dp.toPx()))
            val bottom = measurer.measure("${0.0.toUnit(fahrenheit).toInt()}°", labelStyle)
            drawText(bottom, topLeft = Offset(2.dp.toPx(), baseline - bottom.size.height - 2.dp.toPx()))

            val line = Path()
            hours.forEachIndexed { i, h ->
                val x = stepPx * (i + 0.5f)
                val y = yOf(h.temp)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

            hours.forEachIndexed { i, h ->
                val x = stepPx * (i + 0.5f)
                val y = yOf(h.temp)
                drawCircle(color, 4.dp.toPx(), Offset(x, y))
                val v = measurer.measure(tempText(h.temp, fahrenheit), valueStyle)
                drawText(v, topLeft = Offset(x - v.size.width / 2f, (y - v.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
                val t = measurer.measure(if (i == 0) "Now" else hourLabel(h.time), labelStyle)
                drawText(t, topLeft = Offset(x - t.size.width / 2f, baseline + 6.dp.toPx()))
            }
        }
    }
}

@Composable
fun DaysCard(days: List<Day>, today: LocalDate, fahrenheit: Boolean, onDayClick: (Day) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("Next 7 days")
        val step = 76.dp
        val measurer = rememberTextMeasurer()
        val highColor = MaterialTheme.colorScheme.primary
        val lowColor = MaterialTheme.colorScheme.tertiary
        val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
        val maxAll = days.maxOf { it.max }
        val minAll = days.minOf { it.min }
        val span = (maxAll - minAll).coerceAtLeast(1.0)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                days.forEach { d ->
                    Column(
                        Modifier
                            .width(step)
                            .clickable { onDayClick(d) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dayLabel(d.date, today), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            dateLabel(d.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        WeatherIcon(d.kind, true, Modifier.size(40.dp))
                        Text(
                            d.kind.label(true),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Canvas(Modifier.width(step * days.size).height(150.dp)) {
                val topPad = 24.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val plotH = size.height - topPad - bottomPad
                val stepPx = size.width / days.size

                fun yOf(c: Double): Float = topPad + (1f - ((c - minAll) / span).toFloat()) * plotH

                val high = Path()
                val low = Path()
                days.forEachIndexed { i, d ->
                    val x = stepPx * (i + 0.5f)
                    if (i == 0) {
                        high.moveTo(x, yOf(d.max))
                        low.moveTo(x, yOf(d.min))
                    } else {
                        high.lineTo(x, yOf(d.max))
                        low.lineTo(x, yOf(d.min))
                    }
                }
                drawPath(high, highColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawPath(low, lowColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                days.forEachIndexed { i, d ->
                    val x = stepPx * (i + 0.5f)
                    drawCircle(highColor, 4.dp.toPx(), Offset(x, yOf(d.max)))
                    drawCircle(lowColor, 4.dp.toPx(), Offset(x, yOf(d.min)))
                    val hi = measurer.measure(tempText(d.max, fahrenheit), valueStyle)
                    drawText(hi, topLeft = Offset(x - hi.size.width / 2f, (yOf(d.max) - hi.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
                    val lo = measurer.measure(tempText(d.min, fahrenheit), valueStyle)
                    drawText(lo, topLeft = Offset(x - lo.size.width / 2f, yOf(d.min) + 6.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun SunCard(sunrise: LocalDateTime, sunset: LocalDateTime, now: LocalDateTime, modifier: Modifier = Modifier) {
    val total = Duration.between(sunrise, sunset).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(sunrise, now).toMinutes()
    val progress = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    GlassCard(modifier.fillMaxWidth()) {
        SectionTitle("Sun")
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val r = min(size.width / 2f - 24.dp.toPx(), size.height - 20.dp.toPx())
            val cx = size.width / 2f
            val cy = size.height - 10.dp.toPx()
            drawLine(track, Offset(cx - r - 16.dp.toPx(), cy), Offset(cx + r + 16.dp.toPx(), cy), 2.dp.toPx())
            drawArc(
                track,
                180f,
                180f,
                false,
                Offset(cx - r, cy - r),
                Size(2 * r, 2 * r),
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f)))
            )
            drawArc(
                primary,
                180f,
                180f * progress,
                false,
                Offset(cx - r, cy - r),
                Size(2 * r, 2 * r),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            )
            val angle = Math.PI * (1.0 - progress)
            val sx = cx + r * cos(angle).toFloat()
            val sy = cy - r * sin(angle).toFloat()
            drawCircle(Color(0x55FFC107), 20.dp.toPx(), Offset(sx, sy))
            drawCircle(Color(0xFFFFC107), 10.dp.toPx(), Offset(sx, sy))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Sunrise (East)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(clockLabel(sunrise), style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Sunset (West)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(clockLabel(sunset), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun LifeIndexCard(items: List<LifeItem>) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("Life index")
        items.forEachIndexed { i, item ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(item.value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
