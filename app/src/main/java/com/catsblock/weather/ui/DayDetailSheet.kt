package com.catsblock.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.data.Day
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.LifeKind
import com.catsblock.weather.data.aqiStatus
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.humidityLabel
import com.catsblock.weather.data.lifeIndex
import com.catsblock.weather.data.precipLabel
import com.catsblock.weather.data.precipToday
import com.catsblock.weather.data.uvLevel
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.detail.AdviceRow
import com.catsblock.weather.ui.detail.ArcGauge
import com.catsblock.weather.ui.detail.CardGroup
import com.catsblock.weather.ui.detail.ChartCard
import com.catsblock.weather.ui.detail.ChartLine
import com.catsblock.weather.ui.detail.GaugeRow
import com.catsblock.weather.ui.detail.HourlyChart
import com.catsblock.weather.ui.detail.HourlyStyle
import com.catsblock.weather.ui.detail.Threshold
import com.catsblock.weather.ui.detail.WaterGauge
import com.catsblock.weather.ui.detail.WindCompass
import com.catsblock.weather.ui.detail.entrance
import com.catsblock.weather.ui.typography.GoogleSansFlex
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val RainBlue = Color(0xFF64B5F6)
private val WindBlue = Color(0xFF90B4F0)
private val HumidityCyan = Color(0xFF4DD0E1)
private val TempOrange = Color(0xFFFFB74D)
private val SunAmber = Color(0xFFFFB74D)
private val UvPalette = listOf(Color(0xFF3E8E41), Color(0xFFE5A22B), Color(0xFFE06C0F), Color(0xFFC62F2F), Color(0xFF7B2CA3))
private val AqiPalette = listOf(Color(0xFF66BB6A), Color(0xFFE6C229), Color(0xFFEF8F2B), Color(0xFFE5484D), Color(0xFF8E4EC6), Color(0xFF8A2A47))
private val UvSteps = listOf(3.0, 6.0, 8.0, 11.0)
private val AqiSteps = listOf(50.0, 100.0, 150.0, 200.0, 300.0)

private fun List<Hour>.hourly(select: (Hour) -> Double?): List<Float?> =
    List(24) { h -> firstOrNull { it.time.hour == h }?.let(select)?.toFloat() }

private fun List<Float?>.top(): Float = filterNotNull().maxOrNull() ?: 0f

private fun List<Float?>.bottom(): Float = filterNotNull().minOrNull() ?: 0f

private fun stepOf(value: Double, steps: List<Double>): Int = steps.count { value >= it }

private fun fromUnit(value: Float, fahrenheit: Boolean): Double =
    if (fahrenheit) (value - 32.0) * 5.0 / 9.0 else value.toDouble()

private fun lifeIcon(kind: LifeKind): ImageVector = when (kind) {
    LifeKind.Sun -> Icons.Rounded.WbSunny
    LifeKind.Exercise -> Icons.Rounded.DirectionsRun
    LifeKind.Clothing -> Icons.Rounded.Checkroom
    LifeKind.Flu -> Icons.Rounded.MedicalServices
    LifeKind.Travel -> Icons.Rounded.DirectionsCar
    LifeKind.Mosquito -> Icons.Rounded.BugReport
}

@Composable
fun DayDetailSheet(
    day: Day,
    hours: List<Hour>,
    fahrenheit: Boolean,
    today: LocalDate,
    now: LocalDateTime,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val dayHours = remember(day, hours) { hours.filter { it.time.toLocalDate() == day.date }.sortedBy { it.time } }
    val scroll = rememberScrollState()
    val collapsePx = with(density) { 110.dp.toPx() }
    val collapse by remember { derivedStateOf { (scroll.value / collapsePx).coerceIn(0f, 1f) } }
    val live = day.date == today

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(scheme.background)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DayHero(day, fahrenheit)
                if (dayHours.isEmpty()) {
                    Text(
                        tr("No hourly data for this day."),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    DayBody(day, dayHours, fahrenheit, now, live)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.background)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), tint = scheme.onSurface)
                }
                Text(
                    longDateLabel(day.date),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .graphicsLayer {
                            alpha = collapse
                            translationY = (1f - collapse) * 14.dp.toPx()
                        },
                    color = scheme.onSurface,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DayHero(day: Day, fahrenheit: Boolean) {
    val transition = rememberInfiniteTransition(label = "day-hero")
    val float by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "day-hero-float"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .entrance(0)
            .clip(RoundedCornerShape(32.dp))
    ) {
        WeatherBackground(day.kind, true, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.55f)))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 22.dp)) {
            Text(
                longDateLabel(day.date),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    tempText(day.max, fahrenheit),
                    color = Color.White,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 56.sp,
                    lineHeight = 62.sp,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    tempText(day.min, fahrenheit),
                    modifier = Modifier.padding(bottom = 10.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.Medium,
                    fontSize = 28.sp
                )
            }
            Text(
                day.kind.label(true),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }
        WeatherIcon(
            day.kind,
            true,
            Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .size(84.dp)
                .graphicsLayer { translationY = float.dp.toPx() }
        )
    }
}

@Composable
private fun DayBody(day: Day, dayHours: List<Hour>, fahrenheit: Boolean, now: LocalDateTime, live: Boolean) {
    val windPeak = dayHours.maxBy { it.windSpeed }
    val aqiMax = dayHours.mapNotNull { it.aqi }.maxOrNull()
    val humidity = dayHours.map { it.humidity }.average().roundToInt()
    val avgTemp = dayHours.map { it.temp }.average()
    val avgFeels = dayHours.map { it.feels }.average()
    val marker = if (live) now.hour else null
    val windUnit = LocalUnits.current.wind
    val tempSeries = dayHours.hourly { it.temp.toUnit(fahrenheit) }
    val letters = listOf(compassLabel(0.0), compassLabel(90.0), compassLabel(180.0), compassLabel(270.0))
    val uvTint = UvPalette[stepOf(day.uvMax, UvSteps)]

    ChartCard(tr("Hourly temperature"), 1) {
        HourlyChart(
            lines = listOf(ChartLine(tempSeries, TempOrange, tr("Temperature"))),
            style = HourlyStyle.Line,
            yMin = tempSeries.bottom() - 3f,
            yMax = tempSeries.top() + 3f,
            thresholds = emptyList(),
            nowHour = marker,
            format = { _, v -> tempText(fromUnit(v, fahrenheit), fahrenheit) }
        )
    }

    CardGroup(
        listOf(
            {
                GaugeRow(
                    label = tr("Precipitation"),
                    value = "${f1(day.precipSum)} mm",
                    sub = if (day.precipSum <= 0.0) tr("No precipitation expected.") else "${day.rainChance}% ${tr("chance")}"
                ) { ArcGauge(day.rainChance / 100f, RainBlue, center = "${day.rainChance}%") }
                Spacer(Modifier.height(14.dp))
                AdviceRow(Icons.Rounded.Umbrella, RainBlue, precipToday(day.precipSum), precipLabel(day.precipSum))
            },
            {
                GaugeRow(
                    label = tr("Wind"),
                    value = windText(windPeak.windSpeed, windUnit),
                    sub = "${windShort(windPeak.windSpeed)} · ${tr("From")} ${compassLabel(windPeak.windDir)}"
                ) { WindCompass(windPeak.windDir, letters) }
            },
            {
                GaugeRow(
                    label = tr("Humidity"),
                    value = "$humidity%",
                    sub = humidityLabel(humidity)
                ) { WaterGauge(humidity / 100f, HumidityCyan) }
            },
            {
                GaugeRow(
                    label = tr("UV index"),
                    value = day.uvMax.roundToInt().toString(),
                    sub = uvLevel(day.uvMax)
                ) { ArcGauge((day.uvMax / 12.0).toFloat(), uvTint, segments = UvPalette) }
                Spacer(Modifier.height(14.dp))
                AdviceRow(Icons.Rounded.Shield, uvTint, uvShort(day.uvMax))
            }
        ),
        baseIndex = 2
    )

    if (aqiMax != null) {
        val aqiTint = AqiPalette[stepOf(aqiMax, AqiSteps)]
        CardGroup(
            listOf(
                {
                    GaugeRow(
                        label = tr("Air quality"),
                        value = "AQI ${aqiMax.roundToInt()}",
                        sub = aqiStatus(aqiMax)
                    ) { ArcGauge((aqiMax / 300.0).toFloat(), aqiTint, segments = AqiPalette.take(5)) }
                    Spacer(Modifier.height(14.dp))
                    AdviceRow(Icons.Rounded.Shield, aqiTint, aqiShort(aqiMax))
                }
            ),
            baseIndex = 3
        )
    }

    DaySunCard(day, now, live)

    val precipSeries = dayHours.hourly { it.precip }
    ChartCard(tr("Hourly precipitation"), 5) {
        HourlyChart(
            lines = listOf(ChartLine(precipSeries, RainBlue, tr("Precipitation"))),
            style = HourlyStyle.Bars,
            yMin = 0f,
            yMax = max(3f, precipSeries.top() * 1.25f),
            thresholds = listOf(Threshold(2.5f, "2.5 mm")),
            nowHour = marker,
            format = { _, v -> "${f1(v.toDouble())} mm" }
        )
    }

    val windSeries = dayHours.hourly { windValue(it.windSpeed, windUnit) }
    val moderate = windValue(20.0, windUnit).toFloat()
    ChartCard(tr("Hourly wind"), 6) {
        HourlyChart(
            lines = listOf(ChartLine(windSeries, WindBlue, tr("Wind"))),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = max(moderate * 1.3f, windSeries.top() * 1.25f),
            thresholds = listOf(Threshold(moderate, "${tr("Moderate")} (${moderate.roundToInt()} ${windUnit.label})")),
            nowHour = marker,
            format = { _, v -> "${v.roundToInt()} ${windUnit.label}" },
            arrows = dayHours.hourly { it.windDir }
        )
    }

    val uvSeries = dayHours.hourly { it.uv }
    ChartCard(tr("Hourly UV index"), 7) {
        HourlyChart(
            lines = listOf(ChartLine(uvSeries, uvTint, tr("UV index"))),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = max(12f, uvSeries.top() + 1f),
            thresholds = UvSteps.map { Threshold(it.toFloat(), "${uvLevel(it)} (${it.roundToInt()}.0)") },
            nowHour = marker,
            format = { _, v -> "UV ${f1(v.toDouble())}" },
            bandColors = listOf(0f to UvPalette[0], 3f to UvPalette[1], 6f to UvPalette[2], 8f to UvPalette[3], 11f to UvPalette[4])
        )
    }

    if (aqiMax != null) {
        val aqiSeries = dayHours.hourly { it.aqi }
        ChartCard(tr("Hourly air quality"), 8) {
            HourlyChart(
                lines = listOf(ChartLine(aqiSeries, AqiPalette[stepOf(aqiMax, AqiSteps)], "AQI")),
                style = HourlyStyle.Line,
                yMin = 0f,
                yMax = max(120f, aqiSeries.top() * 1.25f),
                thresholds = listOf(Threshold(50f, "${aqiStatus(25.0)} (50)"), Threshold(100f, "${aqiStatus(75.0)} (100)")),
                nowHour = marker,
                format = { _, v -> "AQI ${v.roundToInt()}" },
                bandColors = listOf(0f to AqiPalette[0], 50f to AqiPalette[1], 100f to AqiPalette[2], 150f to AqiPalette[3])
            )
        }
    }

    val items = lifeIndex(
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
    ChartCard(tr("Life index"), 9) {
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { item ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(lifeIcon(item.kind), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            item.value,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DaySunCard(day: Day, now: LocalDateTime, live: Boolean) {
    val total = Duration.between(day.sunrise, day.sunset).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(day.sunrise, now).toMinutes()
    val target = if (live) (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.5f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, spring(0.8f, Spring.StiffnessVeryLow)) }
    val transition = rememberInfiniteTransition(label = "day-sun")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "day-sun-spin"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "day-sun-pulse"
    )
    val scheme = MaterialTheme.colorScheme
    val track = scheme.onSurface.copy(alpha = 0.22f)
    ChartCard(tr("Sun"), 4) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                daylightText(total.toInt()),
                color = scheme.onSurface,
                fontFamily = GoogleSansFlex.family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp
            )
            Text(
                tr("Daylight"),
                modifier = Modifier.padding(top = 14.dp),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val r = min(size.width / 2f - 24.dp.toPx(), size.height - 20.dp.toPx())
            val cx = size.width / 2f
            val cy = size.height - 8.dp.toPx()
            drawLine(track, Offset(cx - r - 14.dp.toPx(), cy), Offset(cx + r + 14.dp.toPx(), cy), 1.5f.dp.toPx())
            drawArc(
                track,
                180f,
                180f,
                false,
                Offset(cx - r, cy - r),
                Size(2 * r, 2 * r),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            )
            drawArc(
                SunAmber,
                180f,
                180f * progress.value,
                false,
                Offset(cx - r, cy - r),
                Size(2 * r, 2 * r),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            )
            val angle = PI * (1.0 - progress.value)
            val sx = cx + r * cos(angle).toFloat()
            val sy = cy - r * sin(angle).toFloat()
            val glow = 30.dp.toPx() * pulse
            drawCircle(
                Brush.radialGradient(listOf(SunAmber.copy(alpha = 0.55f), Color.Transparent), center = Offset(sx, sy), radius = glow),
                glow,
                Offset(sx, sy)
            )
            for (i in 0 until 10) {
                rotate(spin + i * 36f, Offset(sx, sy)) {
                    drawLine(SunAmber.copy(alpha = 0.85f), Offset(sx, sy - 14.dp.toPx()), Offset(sx, sy - 19.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            drawCircle(SunAmber, 9.dp.toPx(), Offset(sx, sy))
            drawCircle(Color.White.copy(alpha = 0.9f), 4.dp.toPx(), Offset(sx, sy))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SunTime(tr("Sunrise"), clockLabel(day.sunrise))
            SunTime(tr("Sunset"), clockLabel(day.sunset))
        }
    }
}

@Composable
private fun SunTime(label: String, time: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.WbTwilight, null, Modifier.size(22.dp), tint = SunAmber)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(time, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}
