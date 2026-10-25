package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.catsblock.weather.i18n.tr

@Composable
fun UsageStatusBar(status: UsageStatus, modifier: Modifier = Modifier) {
    val nearLimit = status.shortLimit > 0 && status.shortRemaining <= 5
    val visible = status.lockoutSeconds > 0 || status.exhaustedSeconds > 0 || nearLimit
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xCC1C1C1E)) {
            Column(Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                val label = when {
                    status.exhaustedSeconds > 0 ->
                        tr("Provider limit reached. Using the free data source for") + " " + formatWait(status.exhaustedSeconds)
                    status.lockoutSeconds > 0 ->
                        tr("Too many requests. Try again in") + " " + formatWait(status.lockoutSeconds)
                    else -> tr("Approaching the API usage limit")
                }
                Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                Spacer()
                val used = (status.shortLimit - status.shortRemaining).coerceAtLeast(0)
                val fraction = if (status.shortLimit > 0) used.toFloat() / status.shortLimit.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = if (status.exhaustedSeconds > 0 || status.lockoutSeconds > 0) Color(0xFFFF6B6B) else Color(0xFFFFC857)
                )
            }
        }
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
}
