package com.catsblock.weather.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape as Rounded
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.typography.GoogleSansFlex
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.sin

data class HistoryDay(val date: LocalDate, val low: Int, val high: Int, val avg: Int, val hourly: List<Int>)

private const val HERO_SP = 68
private const val HERO_LINE_SP = 72
private const val COLLAPSED_SCALE = 0.34f
private const val BLUR_STEPS = 6

private fun Modifier.surfaceCard(colors: ColorScheme, shape: Shape): Modifier =
    clip(shape)
        .background(colors.surfaceContainerHigh)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.42f), shape)

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
    val dotY = (h * (1f - safeAvg / 100f)).coerceIn(segTop + w / 2f, segBottom - w / 2f)
    val dot = Offset(w / 2f, dotY)
    val trackSize = Size(w, h)
    val segSize = Size(w, segBottom - segTop)
    val segOrigin = Offset(0f, segTop)
    val alpha = 0.42f + selectedProgress * 0.58f
    val dotRadius = w * (0.13f + selectedProgress * 0.11f)
    val ringRadius = w * (0.20f + selectedProgress * 0.12f)
    val dotAlpha = 0.45f + selectedProgress * 0.55f
    onDrawBehind {
        drawRoundRect(colors.onSurface.copy(alpha = 0.08f), size = trackSize, cornerRadius = radius)
        drawRoundRect(brush, topLeft = segOrigin, size = segSize, cornerRadius = radius, alpha = alpha)
        drawCircle(colors.onPrimary.copy(alpha = 0.18f * dotAlpha), ringRadius, dot)
        drawCircle(colors.onPrimary.copy(alpha = dotAlpha), dotRadius, dot)
    }
}

@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "nav-button-shape"
    )
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = Rounded(corner),
        interactionSource = source
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun CardDetailHistoryScreen(
    title: String,
    days: List<HistoryDay>,
    today: LocalDate,
    bandLabel: (Int) -> String,
    advice: (Int) -> String,
    onBack: () -> Unit,
    valueText: (HistoryDay) -> String = { "${it.high}%" },
    rangeText: (Int, Int) -> String = { low, high -> "$low%–$high%" }
) {
    if (days.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val collapseDistance = with(density) { 160.dp.toPx() }
    val scrollProgress by remember(scrollState, collapseDistance) {
        derivedStateOf { (scrollState.value / collapseDistance).coerceIn(0f, 1f) }
    }
    var index by remember(days) { mutableIntStateOf(0) }
    var previousIndex by remember(days) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, days.lastIndex)
    val day = days[safeIndex]
    val weekLow = days.minOf { it.low }
    val weekHigh = days.maxOf { it.high }

    var rootWidth by remember { mutableFloatStateOf(0f) }
    var heroX by remember { mutableFloatStateOf(0f) }
    var heroY by remember { mutableFloatStateOf(0f) }
    var heroWidth by remember { mutableFloatStateOf(0f) }
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val topBarHeight = with(density) { 68.dp.toPx() }
    val edge = with(density) { 18.dp.toPx() }
    val heroLinePx = with(density) { HERO_LINE_SP.sp.toPx() }
    val blurEffects = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Array<RenderEffect?>(BLUR_STEPS) { i ->
                if (i == 0) null else BlurEffect(i * 1.6f, i * 1.6f, TileMode.Decal)
            }
        } else {
            arrayOfNulls<RenderEffect>(BLUR_STEPS)
        }
    }
    val select: (Int) -> Unit = { target ->
        previousIndex = safeIndex
        index = target.coerceIn(0, days.lastIndex)
    }
    val springSpec: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.65f, stiffness = 380f)

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.background, colors.surfaceContainerLow)))
                .onSizeChanged { rootWidth = it.width.toFloat() }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
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
                    NavButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), true, onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NavButton(
                            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            tr("Previous day"),
                            safeIndex > 0
                        ) { select(safeIndex - 1) }
                        AnimatedContent(
                            targetState = safeIndex,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                val forward = targetState >= previousIndex
                                val dir = if (forward) 1 else -1
                                (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it * dir / 2 } + fadeOut()) using SizeTransform(clip = false)
                            },
                            label = "date-navigator"
                        ) { i ->
                            val entry = days[i]
                            Text(
                                "${dayLabel(entry.date, today)} · ${dateLabel(entry.date)}",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        NavButton(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            tr("Next day"),
                            safeIndex < days.lastIndex
                        ) { select(safeIndex + 1) }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .surfaceCard(colors, MaterialTheme.shapes.extraLarge)
                            .padding(horizontal = 24.dp, vertical = 22.dp)
                    ) {
                        Text(
                            tr("Peak cloud cover"),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Spacer(
                            Modifier
                                .height(with(density) { HERO_LINE_SP.sp.toDp() })
                                .fillMaxWidth()
                                .onGloballyPositioned {
                                    val p = it.positionInRoot()
                                    heroX = p.x
                                    heroY = p.y
                                }
                        )
                        Text(
                            bandLabel(day.high),
                            color = colors.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .surfaceCard(colors, MaterialTheme.shapes.extraLarge)
                            .padding(20.dp)
                    ) {
                        Text(
                            tr("Range this week"),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            rangeText(weekLow, weekHigh),
                            color = colors.onSurface,
                            fontFamily = GoogleSansFlex.family,
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1).sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            days.forEachIndexed { dayIndex, entry ->
                                val selected = dayIndex == safeIndex
                                val selectedProgress by animateFloatAsState(
                                    targetValue = if (selected) 1f else 0f,
                                    animationSpec = springSpec,
                                    label = "weekly-pill-selection"
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(22.dp))
                                        .selectable(
                                            selected = selected,
                                            role = Role.RadioButton,
                                            onClick = { select(dayIndex) }
                                        )
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "${weekdayLabel(entry.date.atStartOfDay())} ${rangeText(entry.low, entry.high)}"
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier
                                            .width((22f + 8f * selectedProgress).dp)
                                            .height(116.dp)
                                            .pill(selectedProgress, entry.low, entry.high, entry.avg, colors)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        weekdayLabel(entry.date.atStartOfDay()),
                                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .surfaceCard(colors, MaterialTheme.shapes.extraLarge)
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

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .surfaceCard(colors, MaterialTheme.shapes.extraLarge)
                            .background(colors.primaryContainer.copy(alpha = 0.28f))
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = colors.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                tr("Technical guidance"),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        AnimatedContent(targetState = day.high, label = "guidance") { value ->
                            Text(
                                advice(value),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = valueText(day),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { heroWidth = it.width.toFloat() }
                    .graphicsLayer {
                        val p = scrollProgress
                        val scale = 1f + (COLLAPSED_SCALE - 1f) * p
                        val topBarY = statusTop + (topBarHeight - heroLinePx * scale) / 2f
                        val targetX = (rootWidth - edge - heroWidth * scale).coerceAtLeast(0f)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = heroX + (targetX - heroX) * p
                        translationY = heroY + (topBarY - heroY) * p
                        val step = (sin(p * PI.toFloat()) * (BLUR_STEPS - 1)).toInt().coerceIn(0, BLUR_STEPS - 1)
                        renderEffect = blurEffects[step]
                    },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "hero-value"
            ) { text ->
                Text(
                    text,
                    color = colors.onSurface,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = GoogleSansFlex.weightAt(scrollProgress),
                    fontSize = HERO_SP.sp,
                    lineHeight = HERO_LINE_SP.sp,
                    letterSpacing = (-3).sp,
                    maxLines = 1
                )
            }
        }
    }
}
