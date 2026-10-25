package com.catsblock.weather.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.GovAlert
import com.catsblock.weather.i18n.tr
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GovAlertCard(alerts: List<GovAlert>, offsetSeconds: Int, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) return
    GlassCard(modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(Color(0x55FF6B6B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(24.dp), tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    if (alerts.size == 1) tr("Government weather alert") else tr("Government weather alerts"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            alerts.forEach { alert ->
                Spacer(Modifier.height(12.dp))
                GovAlertRow(alert, offsetSeconds)
            }
        }
    }
}

@Composable
private fun GovAlertRow(alert: GovAlert, offsetSeconds: Int) {
    var expanded by remember { mutableStateOf(false) }
    val title = alert.event.ifBlank { alert.headline }
    val detail = alert.description.ifBlank { alert.headline.takeIf { it != title }.orEmpty() }
    val until = if (alert.endsEpoch > 0L) {
        val time = LocalDateTime.ofEpochSecond(alert.endsEpoch, 0, ZoneOffset.ofTotalSeconds(offsetSeconds))
        tr("Until") + " " + time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    } else {
        ""
    }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val meta = listOf(until, alert.sender).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
