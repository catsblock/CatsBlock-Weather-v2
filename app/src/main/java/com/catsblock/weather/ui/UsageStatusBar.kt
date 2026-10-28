package com.catsblock.weather.ui

import com.catsblock.weather.ui.components.ExpressiveProgressBar
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
    UsageCard(
        title = tr(apiName(source)) + " · " + tr("Free limit used"),
        used = status.usedFor(source).coerceAtLeast(0),
        limit = status.limitFor(source),
        resetSeconds = status.resetFor(source).takeIf { it > 0 } ?: status.exhaustedSeconds,
        lockoutSeconds = status.lockoutSeconds,
        visible = source != WeatherSource.OpenMeteo,
        modifier = modifier
    )
}

@Composable
fun MonthlyUsageBar(status: UsageStatus, source: WeatherSource, modifier: Modifier = Modifier) {
    UsageCard(
        title = tr(apiName(source)) + " · " + tr("Monthly limit used"),
        used = status.monthUsedFor(source).coerceAtLeast(0),
        limit = status.monthLimitFor(source),
        resetSeconds = status.monthResetFor(source),
        lockoutSeconds = 0L,
        visible = source != WeatherSource.OpenMeteo,
        modifier = modifier
    )
}

@Composable
private fun UsageCard(
    title: String,
    used: Int,
    limit: Int,
    resetSeconds: Long,
    lockoutSeconds: Long,
    visible: Boolean,
    modifier: Modifier
) {
    AnimatedVisibility(
        visible = visible && limit > 0,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val fraction = if (limit > 0) (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
        val percent = (fraction * 100f).toInt()
        val exhausted = limit > 0 && used >= limit
        val barColor = when {
            fraction >= 0.9f -> Color(0xFFFF6B6B)
            fraction >= 0.7f -> Color(0xFFFFC857)
            else -> Color(0xFF6BD68B)
        }
        val detail = when {
            exhausted -> tr("Available again in") + " " + formatWait(resetSeconds)
            lockoutSeconds > 0 -> tr("Too many requests. Try again in") + " " + formatWait(lockoutSeconds)
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
                        title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text("$percent%", color = barColor, style = MaterialTheme.typography.labelMedium)
                }
                Spacer()
                ExpressiveProgressBar(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Spacer()
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun apiName(source: WeatherSource): String = when (source) {
    WeatherSource.WeatherApi -> "Weather API"
    WeatherSource.OpenWeather -> "OpenWeather API"
    else -> "Visual API"
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
}
