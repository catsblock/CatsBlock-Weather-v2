package com.catsblock.weather.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.catsblock.weather.data.RegionGuard
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AllowedPollMs = 120_000L
private const val BlockedPollMs = 5_000L
private const val NetworkSettleMs = 1_200L

@Composable
fun RegionGate(content: @Composable () -> Unit) {
    val blocked by RegionGuard.blocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                RegionGuard.refresh()
                delay(if (RegionGuard.blocked.value) BlockedPollMs else AllowedPollMs)
            }
        }
    }

    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        var pending: Job? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun recheck() {
                pending?.cancel()
                pending = scope.launch {
                    delay(NetworkSettleMs)
                    RegionGuard.refresh()
                }
            }

            override fun onAvailable(network: Network) = recheck()

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = recheck()

            override fun onLost(network: Network) = recheck()
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose {
            pending?.cancel()
            runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }

    Crossfade(targetState = blocked, animationSpec = tween(280), label = "region-gate") { isBlocked ->
        if (isBlocked) {
            RegionBlockedScreen(onClose = { (context as? ComponentActivity)?.finishAffinity() })
        } else {
            content()
        }
    }
}

@Composable
private fun RegionBlockedScreen(onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BackHandler(onBack = onClose)
    val transition = rememberInfiniteTransition(label = "region-blob")
    val corner by transition.animateFloat(
        initialValue = 50f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corner"
    )
    Box(
        Modifier.fillMaxSize().background(scheme.background).systemBarsPadding().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(
                Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(percent = corner.toInt()))
                    .background(scheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Block, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(68.dp))
            }
            Text(
                tr("Our service is not available in your region."),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                color = scheme.onBackground,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("Ok", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
