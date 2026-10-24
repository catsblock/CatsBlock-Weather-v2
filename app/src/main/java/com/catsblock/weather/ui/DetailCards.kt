package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.data.Day
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.LifeItem
import com.catsblock.weather.data.LifeKind
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.glass.LocalHazeState
import com.catsblock.weather.ui.glass.frostedGlassCard
import com.catsblock.weather.ui.particles.collideWithParticles
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val HourStep = 68.dp
private val DayStep = 70.dp

val LocalGlass = compositionLocalOf { false }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlass.current
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val hazeState = LocalHazeState.current
    val colors = CardDefaults.cardColors(
        containerColor = if (glass) Color.Transparent else scheme.surfaceContainer.copy(alpha = if (dark) 0.58f else 0.74f),
        contentColor = if (glass) Color.White else scheme.onSurface
    )
    val surface = if (glass) {
        modifier.scrollEdgeFade().frostedGlassCard(hazeState, shape).collideWithParticles()
    } else {
        modifier
    }
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = surface, shape = shape, colors = colors, elevation = elevation, border = null) {
            Column(contentModifier.padding(18.dp), content = content)
        }
    } else {
        Card(modifier = surface, shape = shape, colors = colors, elevation = elevation, border = null) {
            Column(contentModifier.padding(18.dp), content = content)
        }
    }
}

@Composable
fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    icon: ImageVector,
    value: String,
    unit: String?,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    GlassCard(modifier.height(176.dp), onClick, Modifier.fillMaxSize()) {
        CardHeader(icon, title)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 44.sp,
                    lineHeight = 52.sp
                ),
                maxLines = 1
            )
            if (unit != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildCurvePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val midX = (a.x + b.x) / 2f
        path.cubicTo(midX, a.y, midX, b.y, b.x, b.y)
    }
    return path
}

@Composable
fun HoursCard(
    hours: List<Hour>,
    days: List<Day>,
    today: LocalDate,
    fahrenheit: Boolean,
    scrollState: ScrollState,
    title: String = tr("Next 36 hours"),
    showDays: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var selected by remember { mutableIntStateOf(0) }
    GlassCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Schedule, title)
        Spacer(Modifier.height(14.dp))
        if (showDays) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                days.forEachIndexed { i, d ->
                    DayChip(d, today, fahrenheit, selected == i) {
                        selected = i
                        val index = hours.indexOfFirst { it.time.toLocalDate() == d.date }.coerceAtLeast(0)
                        scope.launch {
                            scrollState.animateScrollTo(with(density) { (HourStep * index).roundToPx() })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        HoursChart(hours, fahrenheit, scrollState)
    }
}

@Composable
private fun DayChip(day: Day, today: LocalDate, fahrenheit: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val background = if (selected) scheme.secondaryContainer.copy(alpha = 0.75f) else scheme.onSurface.copy(alpha = 0.06f)
    val content = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    Box(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(dayLabel(day.date, today), style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            Text(
                "${tempText(day.min, fahrenheit)}/${tempText(day.max, fahrenheit)}",
                style = MaterialTheme.typography.titleLarge,
                color = content
            )
            Spacer(Modifier.height(2.dp))
            Text(day.kind.label(true), style = MaterialTheme.typography.bodyMedium, color = content, maxLines = 1)
        }
        WeatherIcon(day.kind, true, Modifier.align(Alignment.TopEnd).size(34.dp))
    }
}

@Composable
private fun HoursChart(hours: List<Hour>, fahrenheit: Boolean, scrollState: ScrollState) {
    if (hours.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val line = scheme.primary
    val grid = scheme.onSurface.copy(alpha = 0.10f)
    val hole = scheme.surfaceContainerHigh
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = scheme.onSurface)
    val minT = hours.minOf { it.temp }
    val maxT = hours.maxOf { it.temp }
    val span = (maxT - minT).coerceAtLeast(4.0)
    val nowLabel = tr("Now")
    Column(Modifier.horizontalScroll(scrollState)) {
        Canvas(Modifier.width(HourStep * hours.size).height(150.dp)) {
            val topPad = 36.dp.toPx()
            val bottomPad = 14.dp.toPx()
            val plotH = size.height - topPad - bottomPad
            val stepPx = size.width / hours.size
            val points = hours.mapIndexed { i, h ->
                Offset(stepPx * (i + 0.5f), topPad + (1f - ((h.temp - minT) / span).toFloat()) * plotH)
            }
            points.forEach { p ->
                drawLine(grid, Offset(p.x, 4.dp.toPx()), Offset(p.x, size.height), 1.dp.toPx())
            }
            val area = buildCurvePath(points)
            area.lineTo(points.last().x, size.height)
            area.lineTo(points.first().x, size.height)
            area.close()
            drawPath(
                area,
                brush = Brush.verticalGradient(
                    listOf(line.copy(alpha = 0.28f), Color.Transparent),
                    startY = topPad,
                    endY = size.height
                )
            )
            drawPath(buildCurvePath(points), line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            hours.forEachIndexed { i, h ->
                val p = points[i]
                drawCircle(line, 6.dp.toPx(), p)
                drawCircle(hole, 3.dp.toPx(), p)
                val v = measurer.measure(tempText(h.temp, fahrenheit), valueStyle)
                drawText(
                    v,
                    topLeft = Offset(p.x - v.size.width / 2f, (p.y - v.size.height - 10.dp.toPx()).coerceAtLeast(0f))
                )
            }
        }
        Row {
            hours.forEachIndexed { i, h ->
                Column(Modifier.width(HourStep), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherIcon(h.kind, h.isDay, Modifier.size(36.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        h.kind.label(h.isDay),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (i == 0) nowLabel else hourLabel(h.time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DaysCard(
    days: List<Day>,
    today: LocalDate,
    fahrenheit: Boolean,
    onDayClick: (Day) -> Unit,
    onMore: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.CalendarMonth, tr("Next 7 days"))
        Spacer(Modifier.height(14.dp))
        DaysChart(days, today, fahrenheit, onDayClick)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onMore)
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tr("Show more"), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DaysChart(days: List<Day>, today: LocalDate, fahrenheit: Boolean, onDayClick: (Day) -> Unit) {
    if (days.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val highColor = scheme.primary
    val lowColor = scheme.tertiary
    val grid = scheme.onSurface.copy(alpha = 0.10f)
    val hole = scheme.surfaceContainerHigh
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = scheme.onSurface)
    val maxAll = days.maxOf { it.max }
    val minAll = days.minOf { it.min }
    val span = (maxAll - minAll).coerceAtLeast(4.0)
    val todayLabel = tr("Today")
    val tomorrowLabel = tr("Tomorrow")
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(DayStep * days.size).height(170.dp)) {
            val topPad = 34.dp.toPx()
            val bottomPad = 34.dp.toPx()
            val plotH = size.height - topPad - bottomPad
            val stepPx = size.width / days.size
            fun yOf(c: Double): Float = topPad + (1f - ((c - minAll) / span).toFloat()) * plotH
            val highs = days.mapIndexed { i, d -> Offset(stepPx * (i + 0.5f), yOf(d.max)) }
            val lows = days.mapIndexed { i, d -> Offset(stepPx * (i + 0.5f), yOf(d.min)) }
            highs.forEach { p -> drawLine(grid, Offset(p.x, 4.dp.toPx()), Offset(p.x, size.height), 1.dp.toPx()) }
            drawPath(buildCurvePath(highs), highColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawPath(buildCurvePath(lows), lowColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            days.forEachIndexed { i, d ->
                val hp = highs[i]
                val lp = lows[i]
                drawCircle(highColor, 6.dp.toPx(), hp)
                drawCircle(hole, 3.dp.toPx(), hp)
                drawCircle(lowColor, 6.dp.toPx(), lp)
                drawCircle(hole, 3.dp.toPx(), lp)
                val hi = measurer.measure(tempText(d.max, fahrenheit), valueStyle)
                drawText(hi, topLeft = Offset(hp.x - hi.size.width / 2f, (hp.y - hi.size.height - 10.dp.toPx()).coerceAtLeast(0f)))
                val lo = measurer.measure(tempText(d.min, fahrenheit), valueStyle)
                drawText(lo, topLeft = Offset(lp.x - lo.size.width / 2f, lp.y + 10.dp.toPx()))
            }
        }
        Row {
            days.forEach { d ->
                Column(
                    Modifier
                        .width(DayStep)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDayClick(d) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WeatherIcon(d.kind, true, Modifier.size(36.dp))
                    Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                        if (d.rainChance >= 30) {
                            Text(
                                "${d.rainChance}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        d.kind.label(true),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (d.date) {
                            today -> todayLabel
                            today.plusDays(1) -> tomorrowLabel
                            else -> dateLabel(d.date)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
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
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val track = scheme.onSurface.copy(alpha = 0.25f)
    GlassCard(modifier.height(176.dp), contentModifier = Modifier.fillMaxSize()) {
        CardHeader(Icons.Rounded.WbTwilight, tr("Sun"))
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.fillMaxWidth().height(62.dp)) {
            val r = min(size.width / 2f - 12.dp.toPx(), size.height - 8.dp.toPx())
            val cx = size.width / 2f
            val cy = size.height - 4.dp.toPx()
            drawLine(track, Offset(cx - r - 10.dp.toPx(), cy), Offset(cx + r + 10.dp.toPx(), cy), 1.5f.dp.toPx())
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
                primary,
                180f,
                180f * progress,
                false,
                Offset(cx - r, cy - r),
                Size(2 * r, 2 * r),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
            val angle = Math.PI * (1.0 - progress)
            val sx = cx + r * cos(angle).toFloat()
            val sy = cy - r * sin(angle).toFloat()
            drawCircle(Color(0x55FFC107), 13.dp.toPx(), Offset(sx, sy))
            drawCircle(Color(0xFFFFC107), 6.dp.toPx(), Offset(sx, sy))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clockLabel(sunrise), style = MaterialTheme.typography.labelLarge)
            Text(clockLabel(sunset), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun lifeIcon(kind: LifeKind): ImageVector = when (kind) {
    LifeKind.Sun -> Icons.Rounded.WbSunny
    LifeKind.Exercise -> Icons.Rounded.DirectionsRun
    LifeKind.Clothing -> Icons.Rounded.Checkroom
    LifeKind.Flu -> Icons.Rounded.MedicalServices
    LifeKind.Travel -> Icons.Rounded.DirectionsCar
    LifeKind.Mosquito -> Icons.Rounded.BugReport
}

@Composable
fun LifeIndexCard(items: List<LifeItem>) {
    GlassCard(Modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.BeachAccess, tr("Life index"))
        Spacer(Modifier.height(8.dp))
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                row.forEach { item ->
                    Column(
                        Modifier.weight(1f).padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(lifeIcon(item.kind), null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
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
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
