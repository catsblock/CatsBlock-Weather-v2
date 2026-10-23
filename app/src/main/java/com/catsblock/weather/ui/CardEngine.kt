package com.catsblock.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.CardId
import com.catsblock.weather.data.CardShape
import com.catsblock.weather.data.CardTheme

val LocalCardId = compositionLocalOf<CardId?> { null }
val LocalCardShape = compositionLocalOf { CardShape.Square }

private fun radiusOf(t: CardTheme): Dp = when (t) {
    CardTheme.Glassmorphism -> 32.dp
    CardTheme.FrostedGlass -> 28.dp
    CardTheme.Neumorphism -> 28.dp
    CardTheme.Claymorphism -> 40.dp
    CardTheme.FlatDesign -> 12.dp
    CardTheme.Skeuomorphism -> 18.dp
    CardTheme.CleanUi -> 20.dp
    CardTheme.Glowmorphism -> 26.dp
    CardTheme.MaterialYou -> 28.dp
    CardTheme.FluentDesign -> 8.dp
    CardTheme.Aqua -> 22.dp
    CardTheme.FrutigerAero -> 30.dp
}

private fun startOf(t: CardTheme, s: ColorScheme): Color = when (t) {
    CardTheme.Glassmorphism, CardTheme.FrostedGlass -> Color.White
    CardTheme.Neumorphism -> Color(0xFF2E3446)
    CardTheme.Claymorphism -> Color(0xFFFFC2D1)
    CardTheme.FlatDesign -> Color(0xFF3B82F6)
    CardTheme.Skeuomorphism -> Color(0xFF6B7080)
    CardTheme.CleanUi -> Color.White
    CardTheme.Glowmorphism -> Color(0xFF00E5FF)
    CardTheme.MaterialYou -> s.secondaryContainer
    CardTheme.FluentDesign -> Color(0xFF3A3A3C)
    CardTheme.Aqua -> Color(0xFF7CCBFF)
    CardTheme.FrutigerAero -> Color(0xFF8BE9FF)
}

private fun endOf(t: CardTheme, s: ColorScheme): Color = when (t) {
    CardTheme.Glassmorphism, CardTheme.FrostedGlass -> Color.White
    CardTheme.Neumorphism -> Color(0xFF242938)
    CardTheme.Claymorphism -> Color(0xFFFFA3B8)
    CardTheme.FlatDesign -> Color(0xFF3B82F6)
    CardTheme.Skeuomorphism -> Color(0xFF2D3039)
    CardTheme.CleanUi -> Color(0xFFEEF2F7)
    CardTheme.Glowmorphism -> Color(0xFFB400FF)
    CardTheme.MaterialYou -> s.secondaryContainer
    CardTheme.FluentDesign -> Color(0xFF2A2A2C)
    CardTheme.Aqua -> Color(0xFF1E6FD9)
    CardTheme.FrutigerAero -> Color(0xFF4FD6A2)
}

private fun contentOf(t: CardTheme, s: ColorScheme): Color = when (t) {
    CardTheme.Claymorphism -> Color(0xFF4A2634)
    CardTheme.CleanUi -> Color(0xFF111827)
    CardTheme.MaterialYou -> s.onSecondaryContainer
    else -> Color.White
}

private fun Modifier.softShadow(fill: Color, shadow: Color, blur: Dp, dx: Dp, dy: Dp, radius: Dp): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = fill.toArgb()
            setShadowLayer(blur.toPx(), dx.toPx(), dy.toPx(), shadow.toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius.toPx(), radius.toPx(), p)
    }
}

private fun Modifier.glowStroke(glow: Color, blur: Dp, width: Dp, radius: Dp): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val p = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = width.toPx()
            this.color = glow.toArgb()
            setShadowLayer(blur.toPx(), 0f, 0f, glow.toArgb())
        }
        val i = width.toPx() / 2f
        canvas.nativeCanvas.drawRoundRect(i, i, size.width - i, size.height - i, radius.toPx(), radius.toPx(), p)
    }
}

@Composable
fun ThemedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentModifier: Modifier = Modifier,
    cardId: CardId? = LocalCardId.current,
    content: @Composable ColumnScope.() -> Unit
) {
    val prefs = LocalUiPrefs.current
    val style = cardId?.let { prefs.styles[it] }
    val theme = style?.theme ?: prefs.defaultTheme
    val scheme = MaterialTheme.colorScheme
    val radius = radiusOf(theme)
    val shape = RoundedCornerShape(radius)
    val c1 = style?.c1?.let { Color(it) }
    val c2 = style?.c2?.let { Color(it) } ?: c1
    val custom = c1 != null
    val b1 = c1 ?: startOf(theme, scheme)
    val b2 = c2 ?: endOf(theme, scheme)
    val alwaysLight = theme == CardTheme.Glassmorphism || theme == CardTheme.FrostedGlass ||
        theme == CardTheme.Glowmorphism || theme == CardTheme.FluentDesign
    val targetContent = when {
        alwaysLight -> Color.White
        custom -> if ((b1.luminance() + b2.luminance()) / 2f > 0.6f) Color(0xFF111827) else Color.White
        else -> contentOf(theme, scheme)
    }
    val cc by animateColorAsState(targetContent, tween(400), label = "cc")
    val white = Color.White
    val black = Color.Black

    val surface: Modifier = when (theme) {
        CardTheme.Glassmorphism -> modifier
            .clip(shape)
            .background(Color(0x26000000))
            .background(
                if (custom) Brush.linearGradient(listOf(b1.copy(alpha = 0.5f), b2.copy(alpha = 0.22f)))
                else Brush.linearGradient(listOf(white.copy(alpha = 0.28f), white.copy(alpha = 0.08f)))
            )
            .border(1.dp, Brush.linearGradient(listOf(white.copy(alpha = 0.6f), white.copy(alpha = 0.08f), white.copy(alpha = 0.3f))), shape)

        CardTheme.FrostedGlass -> modifier
            .clip(shape)
            .background(Color(0x1F000000))
            .background(
                Brush.verticalGradient(
                    listOf(
                        (if (custom) b1 else white).copy(alpha = 0.42f),
                        (if (custom) b2 else white).copy(alpha = 0.22f)
                    )
                )
            )
            .border(1.dp, white.copy(alpha = 0.55f), shape)

        CardTheme.Neumorphism -> modifier
            .softShadow(b2, Color(0xCC000000), 18.dp, 8.dp, 8.dp, radius)
            .softShadow(b1, Color(0x33FFFFFF), 18.dp, (-8).dp, (-8).dp, radius)
            .clip(shape)
            .background(Brush.linearGradient(listOf(b1, b2)))

        CardTheme.Claymorphism -> modifier
            .softShadow(b2, b2.copy(alpha = 0.55f), 22.dp, 0.dp, 10.dp, radius)
            .clip(shape)
            .background(Brush.linearGradient(listOf(b1, b2)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(white.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.width * 0.7f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(black.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 1.0f),
                        radius = size.width * 0.6f
                    )
                )
            }
            .border(3.dp, Brush.linearGradient(listOf(white.copy(alpha = 0.7f), Color.Transparent, black.copy(alpha = 0.12f))), shape)

        CardTheme.FlatDesign -> modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(b1, b2)))

        CardTheme.Skeuomorphism -> modifier
            .softShadow(b2, black.copy(alpha = 0.6f), 14.dp, 0.dp, 8.dp, radius)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(b1, b2)))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(white.copy(alpha = 0.25f), Color.Transparent),
                        endY = size.height * 0.35f
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, black.copy(alpha = 0.3f)),
                        startY = size.height * 0.7f,
                        endY = size.height
                    )
                )
            }
            .border(2.dp, Brush.verticalGradient(listOf(white.copy(alpha = 0.7f), black.copy(alpha = 0.6f))), shape)

        CardTheme.CleanUi -> modifier
            .softShadow(b1, black.copy(alpha = 0.18f), 16.dp, 0.dp, 6.dp, radius)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(b1, b2)))
            .border(0.5.dp, black.copy(alpha = 0.08f), shape)

        CardTheme.Glowmorphism -> modifier
            .glowStroke(b1.copy(alpha = 0.9f), 14.dp, 2.dp, radius)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xCC070B1A), Color(0xCC1A0B2E))))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(b1.copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.1f),
                        radius = size.width * 0.8f
                    )
                )
            }
            .border(1.5.dp, Brush.linearGradient(listOf(b1, b2)), shape)

        CardTheme.MaterialYou -> modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(b1.copy(alpha = 0.94f), b2.copy(alpha = 0.94f))))

        CardTheme.FluentDesign -> modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(b1.copy(alpha = 0.72f), b2.copy(alpha = 0.72f))))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(white.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.1f),
                        radius = size.width * 0.9f
                    )
                )
            }
            .border(1.dp, Brush.linearGradient(listOf(white.copy(alpha = 0.4f), white.copy(alpha = 0.08f))), shape)

        CardTheme.Aqua -> modifier
            .softShadow(b2, b2.copy(alpha = 0.5f), 12.dp, 0.dp, 6.dp, radius)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(b1, b2)))
            .drawBehind {
                val step = 4.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawRect(white.copy(alpha = 0.05f), Offset(0f, y), Size(size.width, step / 2f))
                    y += step
                }
                drawOval(
                    Brush.verticalGradient(
                        listOf(white.copy(alpha = 0.65f), white.copy(alpha = 0.05f)),
                        startY = size.height * 0.02f,
                        endY = size.height * 0.52f
                    ),
                    topLeft = Offset(size.width * 0.04f, size.height * 0.02f),
                    size = Size(size.width * 0.92f, size.height * 0.5f)
                )
            }
            .border(1.5.dp, white.copy(alpha = 0.8f), shape)

        CardTheme.FrutigerAero -> modifier
            .softShadow(b2, b2.copy(alpha = 0.45f), 20.dp, 0.dp, 8.dp, radius)
            .clip(shape)
            .background(Brush.linearGradient(listOf(b1, b2)))
            .drawBehind {
                drawCircle(white.copy(alpha = 0.18f), size.height * 0.45f, Offset(size.width * 0.9f, size.height * 0.1f))
                drawCircle(white.copy(alpha = 0.12f), size.height * 0.2f, Offset(size.width * 0.15f, size.height * 0.9f))
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, white.copy(alpha = 0.22f)),
                        startY = size.height * 0.6f,
                        endY = size.height
                    )
                )
            }
            .border(1.5.dp, Brush.linearGradient(listOf(white.copy(alpha = 0.9f), white.copy(alpha = 0.2f), white.copy(alpha = 0.7f))), shape)
    }

    val dark = cc.luminance() < 0.5f
    val themed = remember(scheme, cc) {
        scheme.copy(
            onSurface = cc,
            onSurfaceVariant = cc.copy(alpha = 0.72f),
            primary = if (dark) Color(0xFF1565C0) else Color(0xFFCDE6FF),
            outline = cc.copy(alpha = 0.5f),
            outlineVariant = cc.copy(alpha = 0.25f)
        )
    }
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(surface.animateContentSize().then(click)) {
        CompositionLocalProvider(LocalContentColor provides cc) {
            MaterialTheme(colorScheme = themed, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
                Column(contentModifier.padding(18.dp), content = content)
            }
        }
    }
}
