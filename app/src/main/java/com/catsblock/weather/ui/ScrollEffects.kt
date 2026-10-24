package com.catsblock.weather.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

val LocalEdgeFadeInset = compositionLocalOf<Float?> { null }

@Composable
fun Modifier.scrollEdgeFade(): Modifier {
    val inset = LocalEdgeFadeInset.current ?: return this
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var top by remember { mutableFloatStateOf(Float.NaN) }
    var height by remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { coordinates ->
            top = coordinates.positionInRoot().y
            height = coordinates.size.height.toFloat()
        }
        .graphicsLayer {
            if (top.isNaN() || height <= 0f) return@graphicsLayer
            val topFraction = ((top + height - inset) / height).coerceIn(0f, 1f)
            val bottomFraction = ((screenHeight - top) / (height * 0.9f)).coerceIn(0f, 1f)
            val t = min(topFraction, bottomFraction)
            alpha = t * t * (3f - 2f * t)
        }
}

class ElasticScrollState(
    private val scope: CoroutineScope,
    private val maxPull: Float,
    private val threshold: Float,
    private val holdOffset: Float,
    private val onRefresh: () -> Boolean
) {
    var offset by mutableFloatStateOf(0f)
        private set

    fun settle(hold: Boolean) {
        val from = offset
        val target = if (hold) holdOffset else 0f
        scope.launch {
            animate(
                initialValue = from,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)
            ) { value, _ -> offset = value }
        }
    }

    fun endRefresh() {
        if (offset > 0f) settle(false)
    }

    fun release() {
        val pulledDown = offset >= threshold
        val accepted = pulledDown && onRefresh()
        settle(accepted)
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || offset == 0f || available.y == 0f) return Offset.Zero
            if (sign(available.y) == sign(offset)) return Offset.Zero
            val next = offset + available.y
            val clamped = if (sign(next) != sign(offset)) 0f else next
            val consumed = clamped - offset
            offset = clamped
            return Offset(0f, consumed)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            val resistance = max(0.12f, 1f - abs(offset) / maxPull) * 0.5f
            offset = (offset + available.y * resistance).coerceIn(-maxPull * 0.45f, maxPull)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset == 0f) return Velocity.Zero
            release()
            return available
        }
    }
}
