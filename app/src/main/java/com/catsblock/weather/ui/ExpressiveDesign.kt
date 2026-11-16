package com.catsblock.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ExpressiveMotion {
    @Composable
    fun <T> shapeChange() = MaterialTheme.motionScheme.defaultSpatialSpec<T>()

    @Composable
    fun <T> contentChange() = MaterialTheme.motionScheme.defaultEffectsSpec<T>()
}

object WeatherExpressiveShapes {
    val section = RoundedCornerShape(topStart = 28.dp, topEnd = 18.dp, bottomEnd = 28.dp, bottomStart = 18.dp)
    val hero = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    val tile = RoundedCornerShape(topStart = 24.dp, topEnd = 14.dp, bottomEnd = 24.dp, bottomStart = 14.dp)
    val inset = RoundedCornerShape(18.dp)
}

@Composable
fun ExpressiveSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = WeatherExpressiveShapes.section,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier, shape = shape, color = color, content = content)
}
