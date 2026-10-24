package com.catsblock.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.HubAlert
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.particles.collideWithParticles
import java.time.Instant
import java.time.ZoneId

private val AlertRed = Color(0xFFD32F2F)
private val AlertRedDark = Color(0xFF9F1B1B)

private fun alertTime(ms: Long): String {
    val time = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "${dateLabel(time.toLocalDate())}, ${clockLabel(time)}"
}

private fun alertWindow(alert: HubAlert): String? {
    val from = alert.effective
    val to = alert.expires
    return when {
        from != null && to != null -> "${alertTime(from)} - ${alertTime(to)}"
        to != null -> "${tr("Until")} ${alertTime(to)}"
        from != null -> "${tr("From")} ${alertTime(from)}"
        else -> null
    }
}

@Composable
fun HubAlertCard(alerts: List<HubAlert>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val top = alerts.firstOrNull() ?: return
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        modifier
            .fillMaxWidth()
            .scrollEdgeFade()
            .collideWithParticles()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AlertRed, AlertRedDark)))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Warning, null, Modifier.size(26.dp), tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                top.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (top.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    top.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (alerts.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "+${alerts.size - 1} ${tr("more")}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.85f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubAlertsSheet(alerts: List<HubAlert>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, Modifier.size(26.dp), tint = AlertRed)
                Spacer(Modifier.width(10.dp))
                Text(tr("Weather alerts"), style = MaterialTheme.typography.titleLarge)
            }
            alerts.forEach { HubAlertDetail(it) }
            Text(
                "${tr("Source")}: Alert-Hub.org",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HubAlertDetail(alert: HubAlert) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AlertRed.copy(alpha = 0.12f))
            .border(1.dp, AlertRed.copy(alpha = 0.45f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink)

        val tags = listOf(alert.severity, alert.urgency, alert.certainty)
            .filter { it.isNotBlank() && it != "Unknown" }
            .joinToString("  ·  ") { tr(it) }
        if (tags.isNotBlank()) {
            Text(tags, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = AlertRed)
        }

        alertWindow(alert)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = soft) }

        if (alert.area.isNotBlank()) {
            Text(alert.area, style = MaterialTheme.typography.bodySmall, color = soft)
        }

        if (alert.description.isNotBlank()) {
            Text(alert.description, style = MaterialTheme.typography.bodyMedium, color = ink)
        }

        if (alert.instruction.isNotBlank()) {
            Text(tr("What to do"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink)
            Text(alert.instruction, style = MaterialTheme.typography.bodyMedium, color = ink)
        }

        if (alert.sender.isNotBlank()) {
            Text("${tr("Issued by")} ${alert.sender}", style = MaterialTheme.typography.labelMedium, color = soft)
        }

        if (alert.web.startsWith("http")) {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
            ) { Text(tr("Open official page")) }
        }
    }
}
