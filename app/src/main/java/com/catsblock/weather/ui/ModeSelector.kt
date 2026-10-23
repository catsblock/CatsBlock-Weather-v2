package com.catsblock.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class DashboardMode {
    Temperature,
    Rain,
    AirQuality
}

@Composable
fun ModeSelector(
    mode: DashboardMode,
    onMode: (DashboardMode) -> Unit,
    modifier: Modifier = Modifier
) {
}
