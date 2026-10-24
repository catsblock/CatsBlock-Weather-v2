package com.catsblock.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Eclipse
import com.catsblock.weather.data.Moon
import com.catsblock.weather.data.MoonPhase
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private val EclipseAccent = Color(0xFFFF8A65)
private val SoftWhite = Color(0xB3FFFFFF)

fun DrawScope.drawEclipse(eclipse: Eclipse, c: Offset, r: Float) {
    val shadow = Offset(c.x + (eclipse.dx * r).toFloat(), c.y + (eclipse.dy * r).toFloat())
    val umbraRadius = (eclipse.umbra * r).toFloat().coerceAtLeast(1f)
    val penumbraRadius = (eclipse.penumbra * r).toFloat().coerceAtLeast(umbraRadius + 1f)
    val inner = (umbraRadius / penumbraRadius).coerceIn(0.05f, 0.95f)
    val moon = Path().apply { addOval(Rect(c, r)) }
    clipPath(moon) {
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0x66140A0A),
                    inner to Color(0x4D140A0A),
                    1f to Color.Transparent
                ),
                center = shadow,
                radius = penumbraRadius
            ),
            penumbraRadius,
            shadow
        )
        if (eclipse.kind >= 1) {
            val strength = if (eclipse.kind == 2) 1f else 0.9f
            drawCircle(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF4A0E08).copy(alpha = 0.96f * strength),
                        0.55f to Color(0xFF8E2012).copy(alpha = 0.94f * strength),
                        0.85f to Color(0xFFC4421E).copy(alpha = 0.90f * strength),
                        1f to Color(0xFFE2743A).copy(alpha = 0.55f * strength)
                    ),
                    center = shadow,
                    radius = umbraRadius
                ),
                umbraRadius,
                shadow
            )
        }
    }
}

@Composable
fun MoonOrb(cycle: Double, modifier: Modifier = Modifier, eclipse: Eclipse? = null) {
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val r = min(size.width, size.height) / 2f * 0.88f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF), Color.Transparent),
                center = c,
                radius = r * 1.45f
            ),
            r * 1.45f,
            c
        )
        val circle = Path().apply { addOval(Rect(c, r)) }
        clipPath(circle) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFF7F5F0), Color(0xFFE3DFD6), Color(0xFFC9C5BB)),
                    center = Offset(c.x - r * 0.3f, c.y - r * 0.3f),
                    radius = r * 1.7f
                ),
                r,
                c
            )
            val half = Path().apply {
                addRect(
                    if (cycle < 0.5) Rect(c.x, c.y - r, c.x + r, c.y + r) else Rect(c.x - r, c.y - r, c.x, c.y + r)
                )
            }
            val k = cos(2.0 * PI * cycle).toFloat()
            val ellipse = Path().apply {
                addOval(Rect(c.x - r * abs(k), c.y - r, c.x + r * abs(k), c.y + r))
            }
            val lit = Path.combine(PathOperation.Intersect, circle, half)
            val shape = Path.combine(if (k > 0f) PathOperation.Difference else PathOperation.Union, lit, ellipse)
            val dark = Path.combine(PathOperation.Difference, circle, shape)
            drawPath(dark, Color(0xFF070B14).copy(alpha = 0.88f))
            drawCircle(
                Brush.radialGradient(
                    colorStops = arrayOf(0.72f to Color.Transparent, 1f to Color(0x55000000)),
                    center = c,
                    radius = r
                ),
                r,
                c
            )
            if (eclipse != null) drawEclipse(eclipse, c, r)
        }
        drawCircle(Color.White.copy(alpha = 0.22f), r, c, style = Stroke(1.dp.toPx()))
    }
}

private fun moonCap(text: String): String = text.replaceFirstChar { it.titlecase(I18n.lang.locale) }

private fun moonTimeLabel(date: LocalDate, time: LocalTime?): String =
    if (time == null) "—" else clockLabel(date.atTime(time))

@Composable
private fun MoonChip(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.02f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = SoftWhite)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SoftWhite)
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
private fun IlluminationBar(fraction: Float) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "illumination")
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.16f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun LiveMoonButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "livePulse"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0x33CDE6FF), Color(0x14FFFFFF))))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        val sc = 1f + pulse * 1.2f
                        scaleX = sc
                        scaleY = sc
                        alpha = 0.5f * (1f - pulse)
                    }
                    .clip(CircleShape)
                    .background(Color(0xFFFF5A5F))
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5A5F)))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr("LIVE moon"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                tr("Point your phone at the sky and follow the arrow."),
                style = MaterialTheme.typography.bodySmall,
                color = SoftWhite
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.CameraAlt, null, Modifier.size(22.dp), tint = Color.White)
    }
}

@Composable
fun MoonCard(city: City, offsetSeconds: Int, now: LocalDateTime, onClick: () -> Unit) {
    val offset = remember(offsetSeconds) { ZoneOffset.ofTotalSeconds(offsetSeconds) }
    val ms = remember(now, offset) { now.toInstant(offset).toEpochMilli() }
    val phase = remember(ms / 60_000L) { Moon.phase(ms) }
    val eclipse = remember(ms / 60_000L) { Moon.eclipse(ms) }
    val date = now.toLocalDate()
    val times = remember(date, city.lat, city.lon, offset) { Moon.riseSet(date, city.lat, city.lon, offset) }
    GlassCard(Modifier.fillMaxWidth(), onClick) {
        CardHeader(Icons.Rounded.NightsStay, tr("Moon"))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr(Moon.phaseName(phase.cycle)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${(phase.illumination * 100).roundToInt()}% ${tr("illuminated")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftWhite
                )
                Spacer(Modifier.height(14.dp))
                IlluminationBar(phase.illumination.toFloat())
                if (eclipse != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(Moon.eclipseName(eclipse)),
                        style = MaterialTheme.typography.labelLarge,
                        color = EclipseAccent
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            MoonOrb(phase.cycle, Modifier.size(104.dp), eclipse)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoonChip(Icons.Rounded.ArrowUpward, tr("Moonrise"), moonTimeLabel(date, times.first), Modifier.weight(1f))
            MoonChip(Icons.Rounded.ArrowDownward, tr("Moonset"), moonTimeLabel(date, times.second), Modifier.weight(1f))
        }
    }
}

private class MoonDayInfo(val date: LocalDate, val phase: MoonPhase, val eclipse: Eclipse?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonCalendarSheet(
    city: City,
    offsetSeconds: Int,
    now: LocalDateTime,
    onLiveMoon: () -> Unit,
    onDismiss: () -> Unit
) {
    val offset = remember(offsetSeconds) { ZoneOffset.ofTotalSeconds(offsetSeconds) }
    val locale = I18n.lang.locale
    val today = now.toLocalDate()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val days = remember(month, offset) {
        (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val noon = date.atTime(12, 0).toInstant(offset).toEpochMilli()
            MoonDayInfo(date, Moon.phase(noon), Moon.dayEclipse(date, offset)?.second)
        }
    }
    val shown = days.firstOrNull { it.date == selected } ?: days.first()
    val shownTimes = remember(shown.date, city.lat, city.lon, offset) {
        Moon.riseSet(shown.date, city.lat, city.lon, offset)
    }
    val shownEclipseTime = remember(shown.date, offset) {
        Moon.dayEclipse(shown.date, offset)?.first?.let {
            clockLabel(Instant.ofEpochMilli(it).atOffset(offset).toLocalDateTime())
        }
    }
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val blanks = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val weekdays = remember(firstDay, locale) {
        (0 until 7).map { moonCap(firstDay.plus(it.toLong()).getDisplayName(TextStyle.SHORT, locale)) }
    }
    val cells: List<MoonDayInfo?> = List(blanks) { null } + days
    val rows = cells.chunked(7)
    val tile = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10141C),
        contentColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = Color.White)
                }
                Text(
                    moonCap(month.month.getDisplayName(TextStyle.FULL, locale)) + " " + month.year,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                weekdays.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = SoftWhite,
                        textAlign = TextAlign.Center
                    )
                }
            }
            rows.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in 0 until 7) {
                        val info = week.getOrNull(i)
                        if (info == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val isSelected = info.date == shown.date
                            val isToday = info.date == today
                            val ring = when {
                                isSelected -> Color(0xFFCDE6FF)
                                isToday -> Color.White.copy(alpha = 0.45f)
                                else -> Color.White.copy(alpha = 0.10f)
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(0.78f)
                                    .clip(tile)
                                    .background(Color.White.copy(alpha = if (isSelected) 0.14f else 0.06f))
                                    .border(if (isSelected) 2.dp else 1.dp, ring, tile)
                                    .clickable { selected = info.date }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                MoonOrb(info.phase.cycle, Modifier.size(32.dp), info.eclipse)
                                Text(
                                    info.date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val panel = RoundedCornerShape(24.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(panel)
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(1.dp, Color.White.copy(alpha = 0.09f), panel)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(longDateLabel(shown.date), style = MaterialTheme.typography.labelLarge, color = SoftWhite)
                        Text(
                            tr(Moon.phaseName(shown.phase.cycle)),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            "${(shown.phase.illumination * 100).roundToInt()}% ${tr("illuminated")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftWhite
                        )
                        if (shown.eclipse != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tr(Moon.eclipseName(shown.eclipse)) + (shownEclipseTime?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                color = EclipseAccent
                            )
                        }
                    }
                    MoonOrb(shown.phase.cycle, Modifier.size(112.dp), shown.eclipse)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoonChip(Icons.Rounded.ArrowUpward, tr("Moonrise"), moonTimeLabel(shown.date, shownTimes.first), Modifier.weight(1f))
                MoonChip(Icons.Rounded.ArrowDownward, tr("Moonset"), moonTimeLabel(shown.date, shownTimes.second), Modifier.weight(1f))
            }
            LiveMoonButton {
                onLiveMoon()
                onDismiss()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
