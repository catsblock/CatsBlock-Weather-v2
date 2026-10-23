package com.catsblock.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.catsblock.weather.i18n.tr

enum class DashboardMode(val label: String) {
    Temperature("Temperature"),
    Rain("Rain"),
    AirQuality("Air Quality")
}

@Composable
fun ModeSelector(
    mode: DashboardMode,
    onMode: (DashboardMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color(0x40000000))
            .padding(3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        DashboardMode.entries.forEach { entry ->
            val selected = entry == mode
            val bg by animateColorAsState(
                if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent,
                tween(220),
                label = "modeBg"
            )
            val tint by animateColorAsState(
                if (selected) Color(0xFF0B1220) else Color.White,
                tween(220),
                label = "modeTint"
            )
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onMode(entry) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    when (entry) {
                        DashboardMode.Temperature -> Icons.Rounded.Thermostat
                        DashboardMode.Rain -> Icons.Rounded.WaterDrop
                        DashboardMode.AirQuality -> Icons.Rounded.Air
                    },
                    contentDescription = tr(entry.label),
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                if (selected) {
                    androidx.compose.material3.Text(
                        tr(entry.label),
                        color = tint,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}
