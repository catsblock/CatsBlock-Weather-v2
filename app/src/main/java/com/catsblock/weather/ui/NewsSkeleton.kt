package com.catsblock.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "newsShimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "newsShimmerPhase"
    )
}

private fun Modifier.shimmer(phase: State<Float>, base: Color, highlight: Color): Modifier = drawBehind {
    val sweep = size.width.coerceAtLeast(1f) * 1.8f
    val start = -sweep + phase.value * (size.width + sweep)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(start, 0f),
            end = Offset(start + sweep, 0f)
        )
    )
}

@Composable
private fun SkeletonBar(
    fraction: Float,
    height: Dp,
    phase: State<Float>,
    base: Color,
    highlight: Color,
    corner: Dp = 6.dp
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .shimmer(phase, base, highlight)
    )
}

@Composable
fun NewsSkeletonFeed(modifier: Modifier = Modifier, count: Int = 6) {
    val phase = rememberShimmerPhase()
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val highlight = lerp(base, scheme.onSurface, 0.05f)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(count) {
            SkeletonCard(phase, base, highlight)
        }
    }
}

@Composable
fun NewsSkeletonCard(modifier: Modifier = Modifier) {
    val phase = rememberShimmerPhase()
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    SkeletonCard(phase, base, lerp(base, scheme.onSurface, 0.05f), modifier)
}

@Composable
private fun SkeletonCard(
    phase: State<Float>,
    base: Color,
    highlight: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    SkeletonBar(0.4f, 10.dp, phase, base, highlight, 5.dp)
                    Spacer(Modifier.height(10.dp))
                    SkeletonBar(0.94f, 15.dp, phase, base, highlight)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBar(0.68f, 15.dp, phase, base, highlight)
                    Spacer(Modifier.height(10.dp))
                    SkeletonBar(0.9f, 10.dp, phase, base, highlight, 5.dp)
                    Spacer(Modifier.height(5.dp))
                    SkeletonBar(0.52f, 10.dp, phase, base, highlight, 5.dp)
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .shimmer(phase, base, highlight)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .width(64.dp)
                        .height(26.dp)
                        .clip(CircleShape)
                        .shimmer(phase, base, highlight)
                )
                Box(
                    Modifier
                        .width(48.dp)
                        .height(26.dp)
                        .clip(CircleShape)
                        .shimmer(phase, base, highlight)
                )
                Box(
                    Modifier
                        .width(48.dp)
                        .height(26.dp)
                        .clip(CircleShape)
                        .shimmer(phase, base, highlight)
                )
                Box(
                    Modifier
                        .width(34.dp)
                        .height(26.dp)
                        .clip(CircleShape)
                        .shimmer(phase, base, highlight)
                )
            }
        }
    }
}

@Composable
fun NewsSkeletonMore(modifier: Modifier = Modifier) {
    val phase = rememberShimmerPhase()
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val highlight = lerp(base, scheme.onSurface, 0.05f)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .shimmer(phase, base, highlight)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .shimmer(phase, base, highlight)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.3f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .shimmer(phase, base, highlight)
            )
        }
    }
}
