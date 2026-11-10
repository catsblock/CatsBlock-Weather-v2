package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.AgentMessages
import com.catsblock.weather.data.WorkerHealth
import com.catsblock.weather.i18n.tr

@Composable
fun UsageStatusBar(modifier: Modifier = Modifier) {
    val limited by WorkerHealth.limitReached.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { WorkerHealth.check() }
    AnimatedVisibility(
        visible = limited,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        WorkersLimitNotice()
    }
}

@Composable
private fun WorkersLimitNotice(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Text(tr(AgentMessages.WORKERS_LIMIT), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
