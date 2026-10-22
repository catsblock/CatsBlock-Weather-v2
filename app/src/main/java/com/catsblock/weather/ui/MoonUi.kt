package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Button
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Moon
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MoonDisc(cycle: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF2A2F3C), r, c)
        val circle = Path().apply { addOval(Rect(c, r)) }
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
        drawPath(shape, Color(0xFFF4EFD8))
    }
}

@Composable
fun MoonImage(ms: Long, cycle: Double, modifier: Modifier = Modifier) {
    val url by produceState<String?>(null, ms / 3_600_000L) { value = Moon.imageUrl(ms) }
    Box(modifier.clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
        val u = url
        if (u == null) {
            MoonDisc(cycle, Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = u,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { MoonDisc(cycle, Modifier.fillMaxSize()) },
                error = { MoonDisc(cycle, Modifier.fillMaxSize()) }
            )
        }
    }
}

private fun timeText(date: LocalDate, time: java.time.LocalTime?): String =
    time?.let { clockLabel(date.atTime(it)) } ?: "--"

@Composable
fun MoonCard(
    city: City,
    offsetSeconds: Int,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
    val date = now.toLocalDate()
    val ms = now.toInstant(offset).toEpochMilli()
    val phase = remember(ms / 60_000L) { Moon.phase(ms) }
    val times = remember(city.id, date, offsetSeconds) { Moon.riseSet(date, city.lat, city.lon, offset) }
    GlassCard(modifier.fillMaxWidth(), onClick) {
        CardHeader(Icons.Rounded.NightsStay, tr("Moon"))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonImage(ms, phase.cycle, Modifier.size(96.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(Moon.phaseName(phase.cycle)), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${(phase.illumination * 100).roundToInt()}% ${tr("illuminated")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("${tr("Moonrise")} ${timeText(date, times.first)}", style = MaterialTheme.typography.labelLarge)
                Text("${tr("Moonset")} ${timeText(date, times.second)}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonCalendarSheet(
    city: City,
    offsetSeconds: Int,
    now: LocalDateTime,
    onLive: () -> Unit,
    onDismiss: () -> Unit
) {
    val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
    val today = now.toLocalDate()
    val locale = I18n.lang.locale
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val first = WeekFields.of(locale).firstDayOfWeek
    val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val length = month.lengthOfMonth()
    val rows = (lead + length + 6) / 7

    fun noon(date: LocalDate): Long = date.atTime(12, 0).toInstant(offset).toEpochMilli()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }} ${month.year}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                for (i in 0..6) {
                    Text(
                        first.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val day = row * 7 + col - lead + 1
                        if (day in 1..length) {
                            val date = month.atDay(day)
                            val isSelected = date == selected
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                    .then(
                                        if (date == today) {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selected = date }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(day.toString(), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                MoonDisc(Moon.phase(noon(date)).cycle, Modifier.size(28.dp))
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val selectedMs = noon(selected)
            val selectedPhase = remember(selected) { Moon.phase(selectedMs) }
            val selectedTimes = remember(selected, city.id) { Moon.riseSet(selected, city.lat, city.lon, offset) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonImage(selectedMs, selectedPhase.cycle, Modifier.size(120.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dayLabel(selected, today), style = MaterialTheme.typography.titleMedium)
                    Text(dateLabel(selected), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(tr(Moon.phaseName(selectedPhase.cycle)), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${(selectedPhase.illumination * 100).roundToInt()}% ${tr("illuminated")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${tr("Moonrise")} ${timeText(selected, selectedTimes.first)}", style = MaterialTheme.typography.labelLarge)
                    Text("${tr("Moonset")} ${timeText(selected, selectedTimes.second)}", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onLive, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CameraAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("LIVE moon")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
