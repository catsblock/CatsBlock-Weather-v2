package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.i18n.tr
import java.time.LocalDate

data class HistoryDay(val date: LocalDate, val low: Int, val high: Int, val avg: Int, val hourly: List<Int>)

private val CardShape = RoundedCornerShape(32.dp)

private fun Modifier.expressiveSurface(colors: ColorScheme): Modifier =
    clip(CardShape)
        .background(colors.surfaceContainerHigh)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.42f), CardShape)

private fun Modifier.pill(
    selectedProgress: Float,
    low: Int,
    high: Int,
    avg: Int,
    colors: ColorScheme
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val safeHigh = high.coerceIn(0, 100)
    val safeLow = low.coerceIn(0, safeHigh)
    val safeAvg = avg.coerceIn(safeLow, safeHigh)
    val segTop = (h * (1f - safeHigh / 100f)).coerceIn(0f, h - w)
    val segBottom = (h * (1f - safeLow / 100f)).coerceIn(segTop + w, h)
    val radius = CornerRadius(w / 2f, w / 2f)
    val brush = Brush.verticalGradient(listOf(colors.primary, colors.tertiary), segTop, segBottom)
    val dot = Offset(w / 2f, (h * (1f - safeAvg / 100f)).coerceIn(segTop + w / 2f, segBottom - w / 2f))
    val dotRadius = w * 0.22f
    val trackSize = Size(w, h)
    val segSize = Size(w, segBottom - segTop)
    val segOrigin = Offset(0f, segTop)
    val alpha = 0.45f + selectedProgress * 0.55f
    val animatedDotRadius = w * (0.15f + selectedProgress * 0.07f)
    onDrawBehind {
        drawRoundRect(colors.onSurface.copy(alpha = 0.08f), size = trackSize, cornerRadius = radius)
        drawRoundRect(brush, topLeft = segOrigin, size = segSize, cornerRadius = radius, alpha = alpha)
        if (selectedProgress > 0.01f) {
            drawCircle(colors.onPrimary.copy(alpha = selectedProgress), animatedDotRadius, dot)
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.42f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(23.dp),
            tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.32f)
        )
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

    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val collapseDistance = with(density) { 148.dp.toPx() }
    val scrollProgress by remember(scrollState, collapseDistance) {
        derivedStateOf { (scrollState.value / collapseDistance).coerceIn(0f, 1f) }
    }
    var index by remember(days) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, days.lastIndex)
    val day = days[safeIndex]
    val weekLow = days.minOf { it.low }
    val weekHigh = days.maxOf { it.high }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colors.background, colors.surfaceContainerLow)
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), true, onBack)
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedContent(
                    targetState = day.high,
                    modifier = Modifier
                        .widthIn(min = 62.dp)
                        .graphicsLayer {
                            alpha = scrollProgress
                            scaleX = 0.9f + scrollProgress * 0.1f
                            scaleY = 0.9f + scrollProgress * 0.1f
                        }
                        .semantics {
                            if (scrollProgress < 0.45f) invisibleToUser()
                        },
                    label = "collapsed-cloud-cover"
                ) { value ->
                    Text(
                        "$value%",
                        color = colors.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleButton(
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        tr("Previous day"),
                        safeIndex > 0
                    ) { index = safeIndex - 1 }
                    Text(
                        "${dayLabel(day.date, today)} · ${dateLabel(day.date)}",
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        .expressiveSurface(colors)
                        .padding(horizontal = 24.dp, vertical = 22.dp)
                ) {
                    Text(
                        tr("Peak cloud cover"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(targetState = day.high, label = "daily-peak-cloud-cover") { value ->
                        Text(
                            "$value%",
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - scrollProgress
                                scaleX = 1f - scrollProgress * 0.06f
                                scaleY = 1f - scrollProgress * 0.06f
                            },
                            color = colors.onSurface,
                            fontSize = 68.sp,
                            lineHeight = 72.sp,
                            letterSpacing = (-3).sp,
                            fontWeight = FontWeight.Thin
                        )
                    }
                    Text(
                        bandLabel(day.high),
                        color = colors.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .expressiveSurface(colors)
                        .padding(20.dp)
                ) {
                    Text(
                        tr("Range this week"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$weekLow%–$weekHigh%",
                        color = colors.onSurface,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        days.forEachIndexed { dayIndex, entry ->
                            val selectedProgress by animateFloatAsState(
                                targetValue = if (dayIndex == safeIndex) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "weekly-pill-selection"
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .selectable(
                                        selected = dayIndex == safeIndex,
                                        role = Role.RadioButton,
                                        onClick = { index = dayIndex }
                                    )
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${weekdayLabel(entry.date.atStartOfDay())} ${entry.low}%–${entry.high}%"
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier
                                        .width(22.dp)
                                        .height(116.dp)
                                        .pill(selectedProgress, entry.low, entry.high, entry.avg, colors)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    weekdayLabel(entry.date.atStartOfDay()),
                                    color = if (dayIndex == safeIndex) colors.primary else colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = if (dayIndex == safeIndex) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .expressiveSurface(colors)
                        .padding(20.dp)
                ) {
                    Text(
                        tr("24-hour cloud cover"),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    HourlyCloudChart(day.hourly, day.date)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .expressiveSurface(colors)
                        .padding(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = colors.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        advice(day.high),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}
