package com.catsblock.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Eclipse
import com.catsblock.weather.data.Moon
import com.catsblock.weather.data.MoonPhase
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val SpaceTop = Color(0xFF04050C)
private val SpaceBottom = Color(0xFF0B0E1E)
private val Panel = Color(0xFF12152B)
private val TrackColor = Color(0xFF262848)
private val ElapsedColor = Color(0xFF5B6076)
private val PanelShape = RoundedCornerShape(32.dp)

private class MoonCell(val date: LocalDate, val phase: MoonPhase, val eclipse: Eclipse?)

private class StarDot(val x: Float, val y: Float, val r: Float, val phase: Float)

private class LunarEvents(val newMoon: Int?, val fullMoon: Int?)

private fun nextLunarEvents(from: LocalDate, offset: ZoneOffset): LunarEvents {
    val start = from.atStartOfDay().toInstant(offset).toEpochMilli()
    var previous = Moon.phase(start).cycle
    var newMoon: Int? = null
    var fullMoon: Int? = null
    var step = 1
    while (step <= 220 && (newMoon == null || fullMoon == null)) {
        val ms = start + step * 21_600_000L
        val current = Moon.phase(ms).cycle
        val days = ChronoUnit.DAYS.between(from, Instant.ofEpochMilli(ms).atOffset(offset).toLocalDate()).toInt()
        if (newMoon == null && current < previous - 0.5) newMoon = days
        if (fullMoon == null && previous < 0.5 && current >= 0.5) fullMoon = days
        previous = current
        step++
    }
    return LunarEvents(newMoon, fullMoon)
}

private fun skyScore(illumination: Double, cloud: Int): Float {
    val moonFactor = 1.0 - 0.75 * illumination
    val cloudFactor = if (cloud >= 0) 1.0 - 0.9 * cloud / 100.0 else 1.0
    return (moonFactor * cloudFactor).toFloat().coerceIn(0f, 1f)
}

private fun skyLevel(score: Float): Int = when {
    score < 0.25f -> 0
    score < 0.5f -> 1
    score < 0.75f -> 2
    else -> 3
}

private fun daysText(days: Int?): String = when (days) {
    null -> "—"
    0 -> tr("Today")
    1 -> tr("Tomorrow")
    else -> String.format(Locale.US, tr("in %d days"), days)
}

@Composable
fun MoonScreen(
    city: City,
    offsetSeconds: Int,
    now: LocalDateTime,
    cloud: Int,
    onLiveMoon: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        MoonContent(city, offsetSeconds, now, cloud, onLiveMoon, onDismiss)
    }
}

@Composable
private fun MoonContent(
    city: City,
    offsetSeconds: Int,
    now: LocalDateTime,
    cloud: Int,
    onLiveMoon: () -> Unit,
    onDismiss: () -> Unit
) {
    val offset = remember(offsetSeconds) { ZoneOffset.ofTotalSeconds(offsetSeconds) }
    val locale = I18n.lang.locale
    val today = now.toLocalDate()
    var selected by remember { mutableStateOf(today) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val isToday = selected == today
    val ms = remember(selected, now, offset) {
        if (selected == today) now.toInstant(offset).toEpochMilli()
        else selected.atTime(12, 0).toInstant(offset).toEpochMilli()
    }
    val phase = remember(selected, ms / 60_000L) { Moon.phase(ms) }
    val eclipse = remember(selected, ms / 60_000L) {
        if (selected == today) Moon.eclipse(ms) else Moon.dayEclipse(selected, offset)?.second
    }
    val times = remember(selected, city.lat, city.lon, offset) { Moon.riseSet(selected, city.lat, city.lon, offset) }
    val cells = remember(month, offset) {
        (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val noon = date.atTime(12, 0).toInstant(offset).toEpochMilli()
            MoonCell(date, Moon.phase(noon), Moon.dayEclipse(date, offset)?.second)
        }
    }
    val selectedMonth = YearMonth.from(selected)
    val cycles = remember(selectedMonth, offset) {
        (1..selectedMonth.lengthOfMonth()).map {
            Moon.phase(selectedMonth.atDay(it).atTime(12, 0).toInstant(offset).toEpochMilli()).cycle
        }
    }
    val events = remember(today, offset) { nextLunarEvents(today, offset) }
    val activeCloud = if (isToday) cloud else -1
    val score = skyScore(phase.illumination, activeCloud)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SpaceTop, SpaceBottom, SpaceTop)))) {
        StarField(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                MoonOrb(phase.cycle, Modifier.align(Alignment.Center).size(310.dp), eclipse)
                CloudLayer(activeCloud, Modifier.fillMaxSize())
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tr(Moon.phaseName(phase.cycle)),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(phase.illumination * 100).roundToInt()}% ${tr("illuminated")}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SoftWhite
                    )
                    if (eclipse != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(tr(Moon.eclipseName(eclipse)), style = MaterialTheme.typography.labelLarge, color = EclipseAccent)
                    }
                }
                MonthTimeline(selected, today, cycles, locale)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeTile(tr("Moonrise"), times.first?.let { clockLabel(selected.atTime(it)) } ?: "—", Modifier.weight(1f))
                    TimeTile(tr("Moonset"), times.second?.let { clockLabel(selected.atTime(it)) } ?: "—", Modifier.weight(1f))
                }
                StargazingCard(isToday, score, activeCloud)
                CalendarCard(
                    month = month,
                    cells = cells,
                    selected = selected,
                    today = today,
                    locale = locale,
                    onMonth = { month = it },
                    onSelect = { selected = it }
                )
                UpcomingCard(events)
                LiveMoonButton {
                    onLiveMoon()
                    onDismiss()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Box(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), tint = Color.White)
        }
    }
}

@Composable
private fun StarField(modifier: Modifier) {
    val stars = remember {
        val random = Random(11)
        List(110) { StarDot(random.nextFloat(), random.nextFloat(), 0.5f + random.nextFloat() * 1.3f, random.nextFloat() * 6.28f) }
    }
    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        0f,
        6.2832f,
        infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "twinkle"
    )
    Canvas(modifier) {
        stars.forEach {
            val alpha = (0.4f + 0.35f * sin(twinkle + it.phase)).coerceIn(0.05f, 1f)
            drawCircle(Color.White.copy(alpha = alpha), it.r.dp.toPx(), Offset(it.x * size.width, it.y * size.height))
        }
    }
}

@Composable
private fun CloudLayer(cover: Int, modifier: Modifier) {
    val drift by rememberInfiniteTransition(label = "clouds").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(110_000, easing = LinearEasing)),
        label = "drift"
    )
    val strength = if (cover < 0) 0.5f else (0.12f + 0.88f * cover / 100f).coerceIn(0f, 1f)
    val banks = remember {
        listOf(
            floatArrayOf(0.10f, 0.12f, 1.0f, 0.00f),
            floatArrayOf(0.28f, 0.16f, 0.7f, 0.45f),
            floatArrayOf(0.50f, 0.10f, 1.3f, 0.80f),
            floatArrayOf(0.66f, 0.15f, 0.8f, 0.20f),
            floatArrayOf(0.82f, 0.11f, 1.1f, 0.60f)
        )
    }
    Canvas(modifier) {
        banks.forEach { bank ->
            val cy = size.height * bank[0]
            val radius = size.width * bank[1]
            val travel = (bank[3] + drift * bank[2]) % 1.4f
            val cx = (travel - 0.2f) * size.width
            val center = Offset(cx, cy)
            scale(2.4f, 1f, center) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF3B3F4B).copy(alpha = 0.92f * strength), Color.Transparent),
                        center,
                        radius
                    ),
                    radius,
                    center
                )
            }
        }
    }
}

@Composable
private fun MonthTimeline(selected: LocalDate, today: LocalDate, cycles: List<Double>, locale: Locale) {
    val ym = YearMonth.from(selected)
    val length = ym.lengthOfMonth()
    val fraction = (selected.dayOfMonth - 0.5f) / length
    val fullIndex = cycles.indices.minByOrNull { abs(cycles[it] - 0.5) } ?: 0
    val newIndex = cycles.indices.minByOrNull { min(cycles[it], 1.0 - cycles[it]) } ?: 0
    val fullVisible = abs(cycles[fullIndex] - 0.5) < 0.04
    val newVisible = min(cycles[newIndex], 1.0 - cycles[newIndex]) < 0.04
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp)) {
            val x: Dp = maxWidth * fraction
            Text(
                if (selected == today) tr("Today") else dateLabel(selected),
                Modifier.offset(x = (x - 40.dp).coerceIn(0.dp, maxWidth - 80.dp)).width(80.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val barHeight = 28.dp.toPx()
            val top = (size.height - barHeight) / 2f
            val x = size.width * fraction
            val gap = 7.dp.toPx()
            drawRoundRect(ElapsedColor, Offset(0f, top), Size((x - gap).coerceAtLeast(0f), barHeight), CornerRadius(barHeight / 2f))
            drawRoundRect(TrackColor, Offset(x + gap, top), Size((size.width - x - gap).coerceAtLeast(0f), barHeight), CornerRadius(barHeight / 2f))
            drawRoundRect(
                Color(0xFFC6C9DA),
                Offset(x - 2.dp.toPx(), top - 4.dp.toPx()),
                Size(4.dp.toPx(), barHeight + 8.dp.toPx()),
                CornerRadius(2.dp.toPx())
            )
            if (fullVisible) {
                drawCircle(Color.White.copy(alpha = 0.55f), 3.dp.toPx(), Offset(size.width * (fullIndex + 0.5f) / length, size.height / 2f))
            }
            if (newVisible) {
                drawCircle(Color.White.copy(alpha = 0.35f), 3.dp.toPx(), Offset(size.width * (newIndex + 0.5f) / length, size.height / 2f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                moonCap(ym.minusMonths(1).month.getDisplayName(TextStyle.SHORT, locale)),
                style = MaterialTheme.typography.labelLarge,
                color = SoftWhite
            )
            Text(
                moonCap(ym.month.getDisplayName(TextStyle.SHORT, locale)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                moonCap(ym.plusMonths(1).month.getDisplayName(TextStyle.SHORT, locale)),
                style = MaterialTheme.typography.labelLarge,
                color = SoftWhite
            )
        }
    }
}

@Composable
private fun TimeTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(Panel).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = SoftWhite)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun StargazingCard(isToday: Boolean, score: Float, cloud: Int) {
    val level = skyLevel(score)
    val animated by animateFloatAsState(score.coerceAtLeast(0.08f), spring(), label = "skyScore")
    val color = when (level) {
        0 -> Color(0xFFE53935)
        1 -> Color(0xFFFFA726)
        2 -> Color(0xFF9CCC65)
        else -> Color(0xFF42A5F5)
    }
    val title = tr(listOf("Poor", "Fair", "Good", "Excellent")[level])
    val text = when {
        cloud >= 85 -> tr("Thick clouds will likely block the sky tonight.")
        else -> tr(
            listOf(
                "Bright moonlight will wash out fainter stars. Cloud cover may block the view.",
                "Moonlight is noticeable, but bright stars and planets are still easy to spot.",
                "Dark enough for most stars and constellations. Some haze may soften faint objects.",
                "A dark, clear sky. Faint stars may be visible away from city lights."
            )[level]
        )
    }
    Column(Modifier.fillMaxWidth().clip(PanelShape).background(Panel).padding(22.dp)) {
        Text(
            tr(if (isToday) "Stargazing tonight" else "Stargazing"),
            style = MaterialTheme.typography.titleMedium,
            color = SoftWhite
        )
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(TrackColor, Offset.Zero, size, CornerRadius(size.height / 2f))
            drawRoundRect(color, Offset.Zero, Size(size.width * animated, size.height), CornerRadius(size.height / 2f))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = SoftWhite)
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    cells: List<MoonCell>,
    selected: LocalDate,
    today: LocalDate,
    locale: Locale,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val blanks = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val weekdays = remember(firstDay, locale) {
        (0 until 7).map { firstDay.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale).uppercase(locale) }
    }
    val grid: List<MoonCell?> = List(blanks) { null } + cells
    Column(Modifier.fillMaxWidth().clip(PanelShape).background(Panel).padding(horizontal = 14.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundNav(Icons.Rounded.ChevronLeft, tr("Previous month")) { onMonth(month.minusMonths(1)) }
            Text(
                moonCap(month.month.getDisplayName(TextStyle.FULL, locale)) + " " + month.year,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            RoundNav(Icons.Rounded.ChevronRight, tr("Next month")) { onMonth(month.plusMonths(1)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = SoftWhite,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (i in 0 until 7) {
                    val cell = week.getOrNull(i)
                    if (cell == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val isSelected = cell.date == selected
                        val isToday = cell.date == today
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { onSelect(cell.date) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                cell.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isToday || isSelected) Color.White else SoftWhite
                            )
                            Spacer(Modifier.height(4.dp))
                            MoonOrb(cell.phase.cycle, Modifier.size(34.dp), cell.eclipse)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundNav(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
    ) {
        Icon(icon, description, tint = Color.White)
    }
}

@Composable
private fun UpcomingCard(events: LunarEvents) {
    Column(Modifier.fillMaxWidth().clip(PanelShape).background(Panel).padding(22.dp)) {
        Text(tr("Coming up"), style = MaterialTheme.typography.titleMedium, color = SoftWhite)
        Spacer(Modifier.height(12.dp))
        UpcomingRow(tr("Next New Moon"), daysText(events.newMoon))
        Spacer(Modifier.height(10.dp))
        UpcomingRow(tr("Next Full Moon"), daysText(events.fullMoon))
    }
}

@Composable
private fun UpcomingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = SoftWhite)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
