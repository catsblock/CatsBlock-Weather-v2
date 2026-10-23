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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Eclipse
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

internal fun DrawScope.drawEclipse(e: Eclipse, c: Offset, r: Float) {
    val sc = Offset(c.x + (e.dx * r).toFloat(), c.y - (e.dy * r).toFloat())
    val rp = (e.penumbra * r).toFloat()
    val uf = (e.umbra / e.penumbra).toFloat()
    val brush = Brush.radialGradient(
        0f to Color(0xFF7A220C),
        uf * 0.6f to Color(0xFF983414),
        uf to Color(0xFFD9662A),
        uf * 1.05f to Color(0xFFE9E0D6),
        1f to Color.White,
        center = sc,
        radius = rp
    )
    clipPath(Path().apply { addOval(Rect(c, r)) }) {
        drawRect(brush, blendMode = BlendMode.Multiply)
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
    val eclipse = remember(ms / 60_000L) { Moon.eclipse(ms) }
    val dayEclipse = remember(date, offsetSeconds) { Moon.dayEclipse(date, offset) }
    val times = remember(city.id, date, offsetSeconds) { Moon.riseSet(date, city.lat, city.lon, offset) }
    GlassCard(modifier.fillMaxWidth(), onClick) {
        CardHeader(Icons.Rounded.NightsStay, tr("Moon"))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonSphere3D(ms, city.lat, city.lon, Modifier.size(96.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(Moon.phaseName(phase.cycle)), style = MaterialTheme.typography.titleLarge)
                (eclipse ?: dayEclipse?.second)?.let {
                    Text(
                        tr(Moon.eclipseName(it)),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFF8A65)
                    )
                }
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

    val events = remember(month, offsetSeconds) {
        (1..month.lengthOfMonth()).associate { d ->
            val date = month.atDay(d)
            date to Moon.dayEclipse(date, offset)
        }
    }

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
                                val event = events[date]
                                MoonDisc(
                                    Moon.phase(event?.first ?: noon(date)).cycle,
                                    Modifier.size(28.dp),
                                    event?.second
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val selectedEvent = remember(selected, offsetSeconds) { Moon.dayEclipse(selected, offset) }
            val selectedMs = selectedEvent?.first ?: noon(selected)
            val selectedPhase = remember(selectedMs) { Moon.phase(selectedMs) }
            val selectedTimes = remember(selected, city.id) { Moon.riseSet(selected, city.lat, city.lon, offset) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonImage(selectedMs, selectedPhase.cycle, Modifier.size(120.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dayLabel(selected, today), style = MaterialTheme.typography.titleMedium)
                    Text(dateLabel(selected), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(tr(Moon.phaseName(selectedPhase.cycle)), style = MaterialTheme.typography.titleLarge)
                    selectedEvent?.second?.let {
                        Text(
                            tr(Moon.eclipseName(it)),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFF8A65)
                        )
                    }
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
                Text(tr("LIVE moon"))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
