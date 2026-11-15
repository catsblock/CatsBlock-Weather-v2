package com.catsblock.weather.ui.icons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val CYCLONE_PATH = "M85.52,50C85.52,69.62 69.62,85.52 50,85.52C30.38,85.52 14.48,69.62 14.48,50C14.48,30.38 30.38,14.48 50,14.48C69.62,14.48 85.52,30.38 85.52,50ZM41.71,15.46C53.24,8.85 70.89,10.55 86.98,19.4C77.01,16.29 68.42,15.21 62.56,16.77ZM68.56,19.71C81.39,23.19 92.67,36.87 97.79,54.52C92.94,45.26 87.62,38.43 82.37,35.38ZM84.54,41.71C91.15,53.24 89.45,70.89 80.6,86.98C83.71,77.01 84.79,68.42 83.23,62.56ZM80.29,68.56C76.81,81.39 63.13,92.67 45.48,97.79C54.74,92.94 61.57,87.62 64.62,82.37ZM58.29,84.54C46.76,91.15 29.11,89.45 13.02,80.6C22.99,83.71 31.58,84.79 37.44,83.23ZM31.44,80.29C18.61,76.81 7.33,63.13 2.21,45.48C7.06,54.74 12.38,61.57 17.63,64.62ZM15.46,58.29C8.85,46.76 10.55,29.11 19.4,13.02C16.29,22.99 15.21,31.58 16.77,37.44ZM19.71,31.44C23.19,18.61 36.87,7.33 54.52,2.21C45.26,7.06 38.43,12.38 35.38,17.63ZM60.56,50C60.56,44.17 55.83,39.44 50,39.44C44.17,39.44 39.44,44.17 39.44,50C39.44,55.83 44.17,60.56 50,60.56C55.83,60.56 60.56,55.83 60.56,50Z"
private const val CYCLONE_BODY_PATH = "M85.52,50C85.52,69.62 69.62,85.52 50,85.52C30.38,85.52 14.48,69.62 14.48,50C14.48,30.38 30.38,14.48 50,14.48C69.62,14.48 85.52,30.38 85.52,50ZM41.71,15.46C53.24,8.85 70.89,10.55 86.98,19.4C77.01,16.29 68.42,15.21 62.56,16.77ZM68.56,19.71C81.39,23.19 92.67,36.87 97.79,54.52C92.94,45.26 87.62,38.43 82.37,35.38ZM84.54,41.71C91.15,53.24 89.45,70.89 80.6,86.98C83.71,77.01 84.79,68.42 83.23,62.56ZM80.29,68.56C76.81,81.39 63.13,92.67 45.48,97.79C54.74,92.94 61.57,87.62 64.62,82.37ZM58.29,84.54C46.76,91.15 29.11,89.45 13.02,80.6C22.99,83.71 31.58,84.79 37.44,83.23ZM31.44,80.29C18.61,76.81 7.33,63.13 2.21,45.48C7.06,54.74 12.38,61.57 17.63,64.62ZM15.46,58.29C8.85,46.76 10.55,29.11 19.4,13.02C16.29,22.99 15.21,31.58 16.77,37.44ZM19.71,31.44C23.19,18.61 36.87,7.33 54.52,2.21C45.26,7.06 38.43,12.38 35.38,17.63Z"

val CycloneRed = Color(0xFFCC0000)

private val EyeColor = Color(0xFF10141C)

class CycloneCategory(val label: String, val minKt: Double, val color: Color)

private val Categories = listOf(
    CycloneCategory("TD", 0.0, Color(79, 195, 247)),
    CycloneCategory("TS", 34.0, Color(102, 187, 106)),
    CycloneCategory("C1", 64.0, Color(253, 216, 53)),
    CycloneCategory("C2", 83.0, Color(255, 179, 0)),
    CycloneCategory("C3", 96.0, Color(251, 140, 0)),
    CycloneCategory("C4", 113.0, Color(229, 57, 53)),
    CycloneCategory("C5", 137.0, Color(213, 0, 249))
)

fun cycloneCategory(windKt: Double): CycloneCategory =
    Categories.lastOrNull { windKt >= it.minKt } ?: Categories.first()

fun cycloneColor(windKt: Double): Color {
    if (windKt <= Categories.first().minKt) return Categories.first().color
    for (i in 1 until Categories.size) {
        val a = Categories[i - 1]
        val b = Categories[i]
        if (windKt <= b.minKt) return lerp(a.color, b.color, ((windKt - a.minKt) / (b.minKt - a.minKt)).toFloat())
    }
    return Categories.last().color
}

val CycloneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Cyclone",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 100f,
        viewportHeight = 100f
    ).addPath(
        pathData = addPathNodes(CYCLONE_PATH),
        fill = SolidColor(CycloneRed)
    ).build()
}

private val CycloneBody: ImageVector by lazy {
    ImageVector.Builder(
        name = "CycloneBody",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 100f,
        viewportHeight = 100f
    ).addPath(
        pathData = addPathNodes(CYCLONE_BODY_PATH),
        fill = SolidColor(Color.Black)
    ).build()
}

@Composable
fun CycloneMarker(
    windKt: Double,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    southernHemisphere: Boolean = false,
    rotating: Boolean = true
) {
    val category = cycloneCategory(windKt)
    val color = cycloneColor(windKt)
    val transition = rememberInfiniteTransition(label = "cyclone")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (southernHemisphere) 360f else -360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "cycloneAngle"
    )
    val fontSize = with(LocalDensity.current) { (size * 0.21f).toSp() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = rememberVectorPainter(CycloneBody),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (rotating) angle else 0f }
        )
        Box(
            Modifier
                .size(size * 0.33f)
                .clip(CircleShape)
                .background(EyeColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.label,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
