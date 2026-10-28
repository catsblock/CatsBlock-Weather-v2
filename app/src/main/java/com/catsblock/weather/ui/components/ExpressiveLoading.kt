package com.catsblock.weather.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = Color.Unspecified,
    contained: Boolean = false
) {
    val sized = modifier.size(size)
    if (contained) {
        if (color == Color.Unspecified) ContainedLoadingIndicator(sized) else ContainedLoadingIndicator(sized, indicatorColor = color)
    } else {
        if (color == Color.Unspecified) LoadingIndicator(sized) else LoadingIndicator(sized, color = color)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = if (color == Color.Unspecified) androidx.compose.material3.MaterialTheme.colorScheme.primary else color,
        trackColor = if (trackColor == Color.Unspecified) androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest else trackColor
    )
}
