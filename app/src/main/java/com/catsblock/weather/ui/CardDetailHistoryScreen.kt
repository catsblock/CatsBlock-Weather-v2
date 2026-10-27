package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.i18n.tr
import java.time.LocalDate

data class HistoryDay(val date: LocalDate, val low: Int, val high: Int, val avg: Int, val hourly: List<Int>)

private val ScreenBackground = Brush.verticalGradient(listOf(Color(0xFF0A0F17), Color(0xFF151C28)))
private val SurfaceColor = Color(0xFF1B212C)
private val TrackColor = Color(0x1FFFFFFF)
private val PillTop = Color(0xFFE3EEF8)
private val PillBottom = Color(0xFF7F9FC0)
private val MutedText = Color(0x99FFFFFF)
private val DisabledIcon = Color(0x44FFFFFF)

private fun Modifier.pill(selected: Boolean, low: Int, high: Int, avg: Int): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val segTop = (h * (1f - high / 100f)).coerceAtMost(h - w)
    val segBottom = (h * (1f - low / 100f)).coerceAtLeast(segTop + w)
    val radius = CornerRadius(w / 2f, w / 2f)
    val brush = Brush.verticalGradient(listOf(PillTop, PillBottom), segTop, segBottom)
    val dot = Offset(w / 2f, (h * (1f - avg / 100f)).coerceIn(segTop + w / 2f, segBottom - w / 2f))
    val dotRadius = w * 0.22f
    val trackSize = Size(w, h)
    val segSize = Size(w, segBottom - segTop)
    val segOrigin = Offset(0f, segTop)
    val alpha = if (selected) 1f else 0.5f
    onDrawBehind {
        drawRoundRect(TrackColor, size = trackSize, cornerRadius = radius)
        drawRoundRect(brush, topLeft = segOrigin, size = segSize, cornerRadius = radius, alpha = alpha)
        if (selected) drawCircle(Color.White, dotRadius, dot)
    }
}

@Composable
private fun CircleButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(22.dp), tint = if (enabled) Color.White else DisabledIcon)
    }
}

@Composable
fun CardDetailHistoryScreen(
    title: String,
    days: List<HistoryDay>,
    today: LocalDate,
    bandLabel: (Int) -> String,
    advice: (Int) -> String,
    onBack: () -> Unit
) {
    if (days.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var index by remember { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, days.lastIndex)
    val day = days[safeIndex]
    val weekLow = days.minOf { it.avg }
    val weekHigh = days.maxOf { it.avg }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ScreenBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), true, onBack)
                Spacer(Modifier.width(14.dp))
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    tr("Previous day"),
                    safeIndex > 0
                ) { index = safeIndex - 1 }
                Text(
                    "${dayLabel(day.date, today)}, ${dateLabel(day.date)}",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                CircleButton(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    tr("Next day"),
                    safeIndex < days.lastIndex
                ) { index = safeIndex + 1 }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceColor)
                    .padding(20.dp)
            ) {
                Text("$weekLow%–$weekHigh%", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Text(tr("Range this week"), color = MutedText, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    days.forEachIndexed { i, entry ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    index = i
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .width(26.dp)
                                    .height(132.dp)
                                    .pill(i == safeIndex, entry.low, entry.high, entry.avg)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                weekdayLabel(entry.date.atStartOfDay()),
                                color = if (i == safeIndex) Color.White else MutedText,
                                fontSize = 12.sp,
                                fontWeight = if (i == safeIndex) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceColor)
                    .padding(20.dp)
            ) {
                Text(tr("24-hour cloud cover"), color = MutedText, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                HourlyCloudChart(day.hourly, day.date)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceColor)
                    .padding(20.dp)
            ) {
                Text(tr("Peak cloud cover"), color = MutedText, fontSize = 14.sp)
                Text("${day.high}%", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text(bandLabel(day.high), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceColor)
                    .padding(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(24.dp), tint = Color(0xFF9FC4E8))
                Spacer(Modifier.width(14.dp))
                Text(advice(day.high), color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}
