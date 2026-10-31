package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

enum class WindowWidth { Compact, Medium, Expanded }

@Immutable
data class WindowLayout(
    val width: WindowWidth,
    val landscape: Boolean,
    val widthDp: Int,
    val heightDp: Int
) {
    val isWide: Boolean get() = width != WindowWidth.Compact
    val useRail: Boolean get() = isWide
    val twoColumns: Boolean get() = isWide
    val contentMaxWidth: Dp get() = if (width == WindowWidth.Expanded) 1120.dp else 880.dp
}

val LocalWindowLayout = compositionLocalOf {
    WindowLayout(WindowWidth.Compact, landscape = false, widthDp = 360, heightDp = 800)
}

@Composable
fun rememberWindowLayout(): WindowLayout {
    val config = LocalConfiguration.current
    val w = config.screenWidthDp
    val h = config.screenHeightDp
    return remember(w, h) {
        WindowLayout(
            width = when {
                w >= 840 -> WindowWidth.Expanded
                w >= 600 -> WindowWidth.Medium
                else -> WindowWidth.Compact
            },
            landscape = w > h,
            widthDp = w,
            heightDp = h
        )
    }
}

@Composable
fun detailSidePadding(maxContent: Dp = 760.dp, base: Dp = 16.dp): Dp {
    val layout = LocalWindowLayout.current
    return if (layout.isWide) ((layout.widthDp.dp - maxContent) / 2).coerceAtLeast(base) else base
}

@Composable
fun AdaptivePage(maxWidth: Dp = 720.dp, content: @Composable () -> Unit) {
    if (!LocalWindowLayout.current.isWide) {
        content()
        return
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxHeight()) { content() }
    }
}
