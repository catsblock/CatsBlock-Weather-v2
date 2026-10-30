package com.catsblock.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Solar
import com.catsblock.weather.data.SolarDay
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.detail.entrance
import com.catsblock.weather.ui.typography.GoogleSansFlex
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val Amber = Color(0xFFE8A33D)
private val AmberLight = Color(0xFFFFB74D)
private val CardShape = RoundedCornerShape(32.dp)

internal fun daylightText(minutes: Int): String =
    String.format(Locale.US, tr("%dh %dm"), minutes / 60, minutes % 60)

private fun shortMinutes(minutes: Int): String = String.format(Locale.US, tr("%dm"), minutes)

private fun String.capFirst(): String = replaceFirstChar { it.titlecase(I18n.lang.locale) }

private fun countText(days: Int): String = when (days) {
    0 -> tr("Today")
    1 -> tr("Tomorrow")
    else -> String.format(Locale.US, tr("%d days"), days)
}

private fun minuteOfDay(time: LocalDateTime): Int = time.hour * 60 + time.minute

private class YearData(
    val start: LocalDate,
    val minutes: IntArray,
    val max: Int,
    val min: Int,
    val longest: LocalDate,
    val shortest: LocalDate
)

private fun buildYear(year: Int, lat: Double, lon: Double, offsetSeconds: Int): YearData {
    val start = LocalDate.of(year, 1, 1)
    val minutes = IntArray(start.lengthOfYear()) { Solar.day(start.plusDays(it.toLong()), lat, lon, offsetSeconds).daylightMinutes }
    val max = minutes.max()
    val min = minutes.min()
    return YearData(start, minutes, max, min, start.plusDays(minutes.indexOf(max).toLong()), start.plusDays(minutes.indexOf(min).toLong()))
}

private fun nextSummerSolstice(today: LocalDate, northern: Boolean): LocalDate {
    val candidate = LocalDate.of(today.year, if (northern) 6 else 12, 21)
    return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
}

@Composable
fun SunScreen(city: City, offsetSeconds: Int, now: LocalDateTime, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SunContent(city, offsetSeconds, now, onDismiss)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SunContent(city: City, offsetSeconds: Int, now: LocalDateTime, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val locale = I18n.lang.locale
    val today = now.toLocalDate()
    var selected by remember { mutableStateOf(today) }
    val lat = city.lat
    val lon = city.lon
    val year = remember(selected) { selected.year }
    val data = remember(year, lat, lon, offsetSeconds) { buildYear(year, lat, lon, offsetSeconds) }
    val day = remember(selected, lat, lon, offsetSeconds) { Solar.day(selected, lat, lon, offsetSeconds) }
    val reference = if (selected == today) today.minusWeeks(1) else today
    val referenceDay = remember(reference, lat, lon, offsetSeconds) { Solar.day(reference, lat, lon, offsetSeconds) }
    val delta = day.daylightMinutes - referenceDay.daylightMinutes
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekStart = selected.minusDays(((selected.dayOfWeek.value - firstDay.value + 7) % 7).toLong())
    val week = remember(weekStart, lat, lon, offsetSeconds) {
        (0..6).map { weekStart.plusDays(it.toLong()) }
    }
    val weekMinutes = remember(week, lat, lon, offsetSeconds) {
        week.map { Solar.day(it, lat, lon, offsetSeconds).daylightMinutes }
    }
    val percent = if (data.max > 0) (day.daylightMinutes * 100f / data.max).roundToInt() else 0
    val listState = rememberLazyListState()
    val solstice = remember(today, lat) { nextSummerSolstice(today, lat >= 0.0) }
    val solsticeDays = ChronoUnit.DAYS.between(today, solstice).toInt()
    val dateText = if (selected == today) {
        "${tr("Now")}, ${dateLabel(selected)}"
    } else {
        "${selected.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).capFirst()}, ${dateLabel(selected)}"
    }

    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = statusTop + 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    tr("Sunrise & sunset"),
                    Modifier
                        .padding(start = 8.dp, top = 32.dp, bottom = 12.dp)
                        .graphicsLayer {
                            alpha = 1f - collapse
                            translationY = -collapse * 24.dp.toPx()
                        },
                    color = scheme.onSurface,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-1).sp
                )
            }
            item {
                Box(Modifier.entrance(0)) {
                    SunPathCard(day = day, now = now, live = selected == today)
                }
            }
            item {
                Box(Modifier.entrance(1)) {
                    PanoramaCard(
                        week = week,
                        minutes = weekMinutes,
                        maxMinutes = data.max,
                        selected = selected,
                        today = today,
                        percent = percent,
                        onSelect = { selected = it }
                    )
                }
            }
            stickyHeader {
                Row(
                    Modifier.fillMaxWidth().background(scheme.background).padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dateText,
                        Modifier.weight(1f, fill = false),
                        color = scheme.onSurface,
                        fontFamily = GoogleSansFlex.family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp,
                        maxLines = 1
                    )
                    if (selected != today) {
                        IconButton(onClick = { selected = today }) {
                            Icon(Icons.Rounded.Today, tr("Today"), tint = scheme.onSurface)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    NavButton(Icons.Rounded.ChevronLeft, tr("Previous day"), false) { selected = selected.minusDays(1) }
                    Spacer(Modifier.width(8.dp))
                    NavButton(Icons.Rounded.ChevronRight, tr("Next day"), true) { selected = selected.plusDays(1) }
                }
            }
            item {
                Box(Modifier.entrance(2)) {
                    DaylightCard(
                        day = day,
                        delta = delta,
                        againstToday = selected != today,
                        nowFraction = if (selected == today) minuteOfDay(now) / 1440f else null
                    )
                }
            }
            item { Box(Modifier.entrance(3)) { DetailsCard(day) } }
            item {
                Box(Modifier.entrance(4)) {
                    SeasonalCard(
                        longest = data.longest,
                        longestMinutes = data.max,
                        shortest = data.shortest,
                        shortestMinutes = data.min,
                        solstice = solstice,
                        solsticeDays = solsticeDays
                    )
                }
            }
            item { Box(Modifier.entrance(5)) { AnnualCard(data, selected) } }
            item { MonthsCard(year, data.max, selected.monthValue, lat, lon, offsetSeconds, locale) }
            item { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
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
                tr("Sunrise & sunset"),
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
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, description: String, primary: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "nav-shape"
    )
    val width by animateDpAsState(
        targetValue = if (pressed) 52.dp else 44.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "nav-width"
    )
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(width, 44.dp),
        shape = RoundedCornerShape(corner),
        interactionSource = source,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (primary) scheme.secondaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (primary) scheme.onSecondaryContainer else scheme.onSurfaceVariant
        )
    ) {
        Icon(icon, description, Modifier.size(24.dp))
    }
}

@Composable
private fun SunPathCard(day: SolarDay, now: LocalDateTime, live: Boolean) {
    val rise = day.sunrise?.let { minuteOfDay(it) } ?: 360
    val set = day.sunset?.let { minuteOfDay(it) } ?: 1080
    val current = if (live) minuteOfDay(now) else minuteOfDay(day.noon)
    val span = (set - rise).coerceAtLeast(1)
    val raw = (current - rise).toFloat() / span
    val up = raw in 0f..1f
    val target = raw.coerceIn(0f, 1f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow)) }
    val transition = rememberInfiniteTransition(label = "sun-path")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
        label = "sun-spin"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sun-pulse"
    )
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sun-twinkle"
    )
    val headline = when {
        !live -> daylightText(day.daylightMinutes)
        up -> daylightText(set - current)
        raw < 0f -> daylightText(rise - current)
        else -> daylightText(1440 - current + rise)
    }
    val caption = when {
        !live -> tr("Daylight")
        up -> tr("Daylight left")
        else -> tr("Until sunrise")
    }
    val status = if (up) tr("Sun is up") else tr("Sun is down")
    val k = if (up) sin(PI * progress.value).toFloat().coerceIn(0f, 1f) else 0f
    val top = lerp(Color(0xFF0B1220), Color(0xFF2F7FD0), k)
    val bottom = lerp(lerp(Color(0xFF1B2740), Color(0xFFF2994A), if (up) 0.85f else 0f), Color(0xFF8CC8F4), k)
    val sunAlpha = if (up) 1f else 0.45f
    Box(Modifier.fillMaxWidth().height(252.dp).clip(CardShape)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(top, bottom)))
            val horizon = h - 76.dp.toPx()
            val cx = w / 2f
            val rx = w / 2f - 28.dp.toPx()
            val ry = (horizon - 118.dp.toPx()).coerceAtLeast(48.dp.toPx())
            for (i in 0 until 18) {
                val sx = ((i * 53) % 100) / 100f * w
                val sy = ((i * 37) % 100) / 100f * horizon * 0.7f
                val a = ((1f - k) * (0.35f + 0.35f * sin(twinkle + i))).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = a), (1f + (i % 3) * 0.6f).dp.toPx(), Offset(sx, sy))
            }
            drawArc(
                Color.White.copy(alpha = 0.32f),
                180f,
                180f,
                false,
                Offset(cx - rx, horizon - ry),
                Size(2 * rx, 2 * ry),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            )
            drawArc(
                AmberLight,
                180f,
                180f * progress.value,
                false,
                Offset(cx - rx, horizon - ry),
                Size(2 * rx, 2 * ry),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawRect(Color.Black.copy(alpha = 0.28f), Offset(0f, horizon), Size(w, h - horizon))
            drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, horizon), Offset(w, horizon), 1.5f.dp.toPx())
            val angle = PI * (1.0 - progress.value)
            val c = Offset(cx + rx * cos(angle).toFloat(), horizon - ry * sin(angle).toFloat())
            val glow = 46.dp.toPx() * pulse
            drawCircle(
                Brush.radialGradient(listOf(Amber.copy(alpha = 0.6f * sunAlpha), Color.Transparent), center = c, radius = glow),
                glow,
                c
            )
            for (i in 0 until 12) {
                rotate(spin + i * 30f, c) {
                    drawLine(
                        AmberLight.copy(alpha = 0.85f * sunAlpha),
                        Offset(c.x, c.y - 23.dp.toPx()),
                        Offset(c.x, c.y - 31.dp.toPx()),
                        2.5f.dp.toPx(),
                        StrokeCap.Round
                    )
                }
            }
            drawCircle(AmberLight.copy(alpha = sunAlpha), 16.dp.toPx(), c)
            drawCircle(Color.White.copy(alpha = 0.9f * sunAlpha), 7.dp.toPx(), c)
        }
        Column(Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 22.dp, end = 24.dp)) {
            Text(caption, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
            Text(
                headline,
                color = Color.White,
                fontFamily = GoogleSansFlex.family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                letterSpacing = (-1).sp,
                maxLines = 1
            )
            Text(status, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SunTime(tr("Sunrise"), day.sunrise?.let { clockLabel(it) } ?: "—")
            SunTime(tr("Sunset"), day.sunset?.let { clockLabel(it) } ?: "—", Alignment.End)
        }
    }
}

@Composable
private fun SunTime(label: String, time: String, align: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WbTwilight, null, Modifier.size(18.dp), tint = AmberLight)
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
        }
        Text(time, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PanoramaCard(
    week: List<LocalDate>,
    minutes: List<Int>,
    maxMinutes: Int,
    selected: LocalDate,
    today: LocalDate,
    percent: Int,
    onSelect: (LocalDate) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val locale = I18n.lang.locale
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(
                tr("Daylight panorama"),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                String.format(Locale.US, tr("%d%% of maximum"), percent),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium, fontSize = 34.sp),
                color = scheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEachIndexed { index, date ->
                    val isSelected = date == selected
                    var started by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 50L)
                        started = true
                    }
                    val fraction by animateFloatAsState(
                        if (started && maxMinutes > 0) (minutes[index].toFloat() / maxMinutes).coerceIn(0.05f, 1f) else 0.05f,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                        label = "dayPill"
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onSelect(date) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            daylightText(minutes[index]),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(scheme.onSurface.copy(alpha = 0.07f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isSelected) Amber else Amber.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (isSelected) {
                                    Box(
                                        Modifier
                                            .padding(bottom = 12.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (date == today) tr("Now") else date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).capFirst(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DaylightCard(day: SolarDay, delta: Int, againstToday: Boolean, nowFraction: Float?) {
    val scheme = MaterialTheme.colorScheme
    val deltaText = when {
        delta == 0 -> tr("No change")
        delta > 0 -> String.format(
            tr(if (againstToday) "%s longer than today" else "%s longer than last week"),
            shortMinutes(delta)
        )
        else -> String.format(
            tr(if (againstToday) "%s shorter than today" else "%s shorter than last week"),
            shortMinutes(-delta)
        )
    }
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(top = 20.dp, start = 18.dp, end = 18.dp)) {
            Text(tr("Sunrise & sunset"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                String.format(tr("%s of daylight"), daylightText(day.daylightMinutes)),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp),
                color = scheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (delta != 0) {
                    Icon(
                        if (delta > 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        null,
                        Modifier.size(20.dp),
                        tint = scheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(deltaText, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            DaylightCurve(day, nowFraction)
        }
    }
}

@Composable
private fun DaylightCurve(day: SolarDay, nowFraction: Float?) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurfaceVariant)
    val blue = scheme.primary
    val hole = scheme.surfaceContainerHigh
    val line = scheme.onSurface.copy(alpha = 0.25f)
    val sunriseText = day.sunrise?.let { clockLabel(it) }
    val sunsetText = day.sunset?.let { clockLabel(it) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(day.date) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }
    val halo by rememberInfiniteTransition(label = "now-halo").animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "now-halo-scale"
    )
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        val noonFraction = minuteOfDay(day.noon) / 1440f
        val offset = -cos(PI * day.daylightMinutes / 1440.0).toFloat()
        val top = max(offset + 1f, 0.25f)
        val low = -0.45f
        fun yOf(v: Float): Float = h * (top - v) / (top - low)
        fun valueAt(t: Float): Float = offset + cos(2.0 * PI * (t - noonFraction)).toFloat()
        val horizon = yOf(0f)
        val steps = 160
        val curve = Path()
        val fill = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = t * w
            val y = yOf(valueAt(t))
            if (i == 0) {
                curve.moveTo(x, y)
                fill.moveTo(x, y)
            } else {
                curve.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(w, horizon)
        fill.lineTo(0f, horizon)
        fill.close()
        drawRect(blue.copy(alpha = 0.14f), Offset(0f, horizon), Size(w, h - horizon))
        clipRect(0f, 0f, w * reveal.value, horizon) {
            drawPath(fill, Brush.verticalGradient(listOf(Amber.copy(alpha = 0.5f), Color.Transparent), 0f, horizon))
            drawPath(curve, AmberLight, style = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        clipRect(0f, horizon, w * reveal.value, h) {
            drawPath(curve, blue, style = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawLine(line, Offset(0f, horizon), Offset(w, horizon), 1.dp.toPx())
        fun marker(time: LocalDateTime?, text: String?) {
            if (time == null || text == null) return
            val x = minuteOfDay(time) / 1440f * w
            drawLine(blue.copy(alpha = 0.6f), Offset(x, horizon - 20.dp.toPx()), Offset(x, horizon - 9.dp.toPx()), 1.5f.dp.toPx())
            drawCircle(Amber, 6.dp.toPx(), Offset(x, horizon))
            drawCircle(hole, 2.5f.dp.toPx(), Offset(x, horizon))
            val m = measurer.measure(text, labelStyle)
            drawText(m, topLeft = Offset((x - m.size.width / 2f).coerceIn(4.dp.toPx(), w - m.size.width - 4.dp.toPx()), horizon + 10.dp.toPx()))
        }
        marker(day.sunrise, sunriseText)
        marker(day.sunset, sunsetText)
        if (nowFraction != null) {
            val p = Offset(nowFraction * w, yOf(valueAt(nowFraction)))
            drawCircle(blue.copy(alpha = 0.28f * (2f - halo)), 16.dp.toPx() * halo, p)
            drawCircle(blue, 9.dp.toPx() * reveal.value, p)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Amber.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = AmberLight)
        }
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(value, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailsCard(day: SolarDay) {
    val scheme = MaterialTheme.colorScheme
    val dash = "—"
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            InfoRow(
                Icons.Rounded.WbSunny,
                tr("Solar noon"),
                "${clockLabel(day.noon)} (${day.noonElevation.roundToInt()}°)"
            )
            InfoRow(Icons.Rounded.WbTwilight, tr("First light"), day.firstLight?.let { clockLabel(it) } ?: dash)
            InfoRow(Icons.Rounded.WbTwilight, tr("Last light"), day.lastLight?.let { clockLabel(it) } ?: dash)
            InfoRow(
                Icons.Rounded.Explore,
                tr("Sunset direction"),
                day.sunsetAzimuth?.let { "${compassLabel(it)} ${it.roundToInt()}°" } ?: dash
            )
        }
    }
}

@Composable
private fun SeasonRow(label: String, date: LocalDate, sub: String, subAccent: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        Column(horizontalAlignment = Alignment.End) {
            Text(dateLabel(date), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = if (subAccent) scheme.primary else scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeasonalCard(
    longest: LocalDate,
    longestMinutes: Int,
    shortest: LocalDate,
    shortestMinutes: Int,
    solstice: LocalDate,
    solsticeDays: Int
) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(tr("Seasonal highlights"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SeasonRow(tr("Longest day"), longest, daylightText(longestMinutes), false)
            SeasonRow(tr("Shortest day"), shortest, daylightText(shortestMinutes), false)
            SeasonRow(tr("Next summer solstice"), solstice, countText(solsticeDays), true)
        }
    }
}

@Composable
private fun AnnualCard(data: YearData, selected: LocalDate) {
    val scheme = MaterialTheme.colorScheme
    val locale = I18n.lang.locale
    val line = scheme.onSurface.copy(alpha = 0.08f)
    val blue = scheme.primary
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(data.start) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }
    val halo by rememberInfiniteTransition(label = "annual-halo").animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "annual-halo-scale"
    )
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(tr("Annual daylight"), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width
                val h = size.height
                val n = data.minutes.size
                val span = (data.max - data.min).coerceAtLeast(1).toFloat()
                fun yOf(m: Int): Float = h * 0.1f + (1f - (m - data.min) / span) * h * 0.5f
                val curve = Path()
                val fill = Path()
                for (i in 0 until n) {
                    val x = i / (n - 1f) * w
                    val y = yOf(data.minutes[i])
                    if (i == 0) {
                        curve.moveTo(x, y)
                        fill.moveTo(x, y)
                    } else {
                        curve.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                }
                fill.lineTo(w, h)
                fill.lineTo(0f, h)
                fill.close()
                clipRect(0f, 0f, w * reveal.value, h) {
                    drawPath(fill, Brush.verticalGradient(listOf(Amber.copy(alpha = 0.45f), Color.Transparent)))
                }
                for (m in 0 until 12) {
                    val x = (YearMonth.of(data.start.year, m + 1).atDay(1).dayOfYear - 1) / (n - 1f) * w
                    drawLine(line, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                }
                clipRect(0f, 0f, w * reveal.value, h) {
                    drawPath(curve, Color(0xFFFFD54F), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                val index = (selected.dayOfYear - 1).coerceIn(0, n - 1)
                val x = index / (n - 1f) * w
                val y = yOf(data.minutes[index])
                drawLine(
                    blue.copy(alpha = 0.7f),
                    Offset(x, y),
                    Offset(x, h),
                    1.5f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
                drawCircle(blue.copy(alpha = 0.3f * (2f - halo)), 13.dp.toPx() * halo, Offset(x, y))
                drawCircle(blue, 7.dp.toPx() * reveal.value, Offset(x, y))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                for (m in 1..12) {
                    Text(
                        java.time.Month.of(m).getDisplayName(TextStyle.NARROW, locale).lowercase(locale),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private class MonthRow(val month: Int, val day: SolarDay)

@Composable
private fun MonthsCard(
    year: Int,
    maxMinutes: Int,
    currentMonth: Int,
    lat: Double,
    lon: Double,
    offsetSeconds: Int,
    locale: Locale
) {
    val scheme = MaterialTheme.colorScheme
    val rows = remember(year, lat, lon, offsetSeconds) {
        (1..12).map { MonthRow(it, Solar.day(YearMonth.of(year, it).atDay(15), lat, lon, offsetSeconds)) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { row ->
            val current = row.month == currentMonth
            val shape = when (row.month) {
                1 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
                12 -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                else -> RoundedCornerShape(10.dp)
            }
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(row.month * 40L)
                shown = true
            }
            val target = if (maxMinutes > 0) (row.day.daylightMinutes.toFloat() / maxMinutes).coerceIn(0.3f, 1f) else 0.3f
            val fraction by animateFloatAsState(
                if (shown) target else 0.02f,
                spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
                label = "monthBar"
            )
            Surface(Modifier.fillMaxWidth(), shape = shape, color = scheme.surfaceContainerHigh) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        java.time.Month.of(row.month).getDisplayName(TextStyle.SHORT, locale).capFirst(),
                        Modifier.width(52.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = scheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        row.day.sunrise?.let { clockLabel(it) } ?: "—",
                        Modifier.width(70.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(scheme.onSurface.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (current) scheme.primary else Amber),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                daylightText(row.day.daylightMinutes),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (current) scheme.onPrimary else Color(0xFF2B1B00),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        row.day.sunset?.let { clockLabel(it) } ?: "—",
                        Modifier.width(70.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
