package com.catsblock.weather.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.AgentLimitOptions
import com.catsblock.weather.i18n.leadFull
import com.catsblock.weather.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun agentLimitLabel(minutes: Int): String = if (minutes == 0) tr("No limit") else leadFull(minutes)

internal fun agentUsageText(usedSeconds: Int, limitMinutes: Int): String {
    val used = usedSeconds / 60
    val unit = tr("min")
    return if (limitMinutes > 0) "$used / $limitMinutes $unit" else "$used $unit"
}

@Composable
internal fun FamilyControlsCard(
    limitMinutes: Int,
    usedSeconds: Int,
    onLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FamilyRestroom, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("Family controls"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
            }
            Surface(
                color = scheme.surface.copy(alpha = 0.55f),
                contentColor = scheme.onSurface,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        tr("For parents and guardians"),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(tr("This Agent is an AI that answers weather questions. Replies can be inaccurate and are not safety advice. Messages are sent to Google's Gemini through a CatsBlock server and are not stored. Use the daily time limit below to control how long the Agent can be used on this device each day. Age information is never collected or sold."), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                tr("Daily time limit"),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentLimitOptions.forEach { minutes ->
                    FilterChip(
                        selected = minutes == limitMinutes,
                        onClick = { onLimitChange(minutes) },
                        label = { Text(agentLimitLabel(minutes)) }
                    )
                }
            }
            Text(
                tr("Used today") + ": " + agentUsageText(usedSeconds, limitMinutes),
                style = MaterialTheme.typography.labelLarge
            )
            if (limitMinutes > 0) {
                LinearProgressIndicator(
                    progress = { (usedSeconds / (limitMinutes * 60f)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun AgentLockedBar(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tr("Daily Agent time limit reached"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
            )
            Text(tr("The Agent will be available again tomorrow."), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun AgentLimitBar(until: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val time = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val label = if (time.toLocalDate() == LocalDate.now()) {
        clockLabel(time)
    } else {
        dateLabel(time.toLocalDate()) + " " + clockLabel(time)
    }
    Surface(
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tr("Your Agent usage limit has been reached."),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
            )
            Text(tr("The limit will reset at {time}.").replace("{time}", label), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
