package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.UsageStatus
import com.catsblock.weather.data.WeatherSource
import com.catsblock.weather.i18n.tr

@Composable
fun UsageStatusBar(status: UsageStatus, source: WeatherSource, modifier: Modifier = Modifier) {
    val limit = status.limitFor(source)
    val used = status.usedFor(source).coerceAtLeast(0)
    val visible = source != WeatherSource.OpenMeteo && limit > 0
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val fraction = if (limit > 0) (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
        val percent = (fraction * 100f).toInt()
        val exhausted = limit > 0 && used >= limit
        val wait = status.resetFor(source).takeIf { it > 0 } ?: status.exhaustedSeconds
        val name = if (source == WeatherSource.WeatherApi) tr("Weather API") else tr("Visual API")
        val barColor = when {
            exhausted -> Color(0xFFFF6B6B)
            fraction >= 0.9f -> Color(0xFFFF6B6B)
            fraction >= 0.7f -> Color(0xFFFFC857)
            else -> Color(0xFF6BD68B)
        }
        val detail = when {
            exhausted -> tr("Available again in") + " " + formatWait(wait)
            status.lockoutSeconds > 0 -> tr("Too many requests. Try again in") + " " + formatWait(status.lockoutSeconds)
            else -> "${limit - used} " + tr("requests left")
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        name + " · " + tr("Free limit used"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text("$percent%", color = barColor, style = MaterialTheme.typography.labelMedium)
                }
                Spacer()
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Spacer()
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
}
