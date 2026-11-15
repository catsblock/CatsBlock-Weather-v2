package com.catsblock.weather.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.Moon
import com.catsblock.weather.data.Solar
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.WeatherBackground
import com.catsblock.weather.ui.clockLabel
import com.catsblock.weather.ui.typography.GoogleSansFlex
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

data class SkySimulationInput(
    val kind: Condition,
    val cloud: Int,
    val windKmh: Double,
    val windDirDeg: Double,
    val precipMm: Double,
    val pressureHpa: Double,
    val visibilityM: Double,
    val epochMs: Long
)

private const val DAY_MS = 86_400_000L
private const val TICK_MS = 40L
private const val SIMULATION_SECONDS = 30f

private fun epochOfDayMidnight(epochMs: Long, offsetSeconds: Int): Long {
    val zone = ZoneOffset.ofTotalSeconds(offsetSeconds)
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    return date.atStartOfDay(zone).toInstant().toEpochMilli()
}

@Composable
internal fun SkySimulationCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr("24-hour sky simulation"),
                    color = scheme.onSurface,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 2
                )
                Text(
                    tr("Watch a full day of sky"),
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 2
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SkyDaySimulationDialog(
    kind: Condition,
    latitude: Double,
    longitude: Double,
    offsetSeconds: Int,
    referenceEpochMs: Long,
    cloud: Int,
    windKmh: Double,
    windDirDeg: Double,
    precipMm: Double,
    pressureHpa: Double,
    visibilityM: Double,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF070B12)) {
            SkyDaySimulationContent(
                kind = kind,
                latitude = latitude,
                longitude = longitude,
                offsetSeconds = offsetSeconds,
                referenceEpochMs = referenceEpochMs,
                cloud = cloud,
                windKmh = windKmh,
                windDirDeg = windDirDeg,
                precipMm = precipMm,
                pressureHpa = pressureHpa,
                visibilityM = visibilityM,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun SkyDaySimulationContent(
    kind: Condition,
    latitude: Double,
    longitude: Double,
    offsetSeconds: Int,
    referenceEpochMs: Long,
    cloud: Int,
    windKmh: Double,
    windDirDeg: Double,
    precipMm: Double,
    pressureHpa: Double,
    visibilityM: Double,
    onDismiss: () -> Unit
) {
    val midnight = remember(referenceEpochMs, offsetSeconds) { epochOfDayMidnight(referenceEpochMs, offsetSeconds) }
    var progress by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(true) }
    var epoch by remember { mutableLongStateOf(midnight) }
    val zone = remember(offsetSeconds) { ZoneOffset.ofTotalSeconds(offsetSeconds) }
    LaunchedEffect(playing, midnight) {
        if (!playing) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            val now = System.nanoTime()
            val delta = ((now - last) / 1e9f).coerceIn(0f, 0.25f)
            last = now
            progress += delta / SIMULATION_SECONDS
            if (progress >= 1f) progress -= 1f
            epoch = midnight + (progress * DAY_MS).toLong()
            delay(TICK_MS)
        }
    }
    val instant = remember(epoch, zone) { Instant.ofEpochMilli(epoch).atZone(zone) }
    val sun = remember(epoch) { Solar.position(epoch, latitude, longitude) }
    val moon = remember(epoch) { Moon.position(epoch, latitude, longitude) }
    val phase = remember(epoch) { Moon.phase(epoch) }
    val sunAltitude = Math.toDegrees(sun.altitude)
    val moonAltitude = Math.toDegrees(moon.altitude)
    val isDay = sunAltitude > -3.0
    Box(Modifier.fillMaxSize()) {
        WeatherBackground(
            kind = kind,
            isDay = isDay,
            modifier = Modifier.fillMaxSize(),
            time = instant.toLocalTime(),
            cloud = cloud,
            windKmh = windKmh,
            dayOfYear = instant.dayOfYear,
            windDirDeg = windDirDeg,
            pressureHpa = pressureHpa,
            precipMm = precipMm,
            latitude = latitude,
            longitude = longitude,
            epochMs = epoch,
            visibilityM = visibilityM
        )
        Box(Modifier.fillMaxSize().background(Color(0xFF000000).copy(alpha = 0.18f)))
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Back"), tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    tr("24-hour sky simulation"),
                    color = Color.White,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    clockLabel(instant.toLocalDateTime()),
                    color = Color.White,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.Medium,
                    fontSize = 28.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0B1119).copy(alpha = 0.62f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        tr("Sun"),
                        color = Color.White.copy(alpha = 0.62f),
                        fontFamily = GoogleSansFlex.family,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${sunAltitude.roundToInt()}° · ${sun.azimuth.roundToInt()}°",
                        color = Color.White,
                        fontFamily = GoogleSansFlex.family,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tr("Moon"),
                        color = Color.White.copy(alpha = 0.62f),
                        fontFamily = GoogleSansFlex.family,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${Moon.phaseName(phase.cycle)} · ${(phase.illumination * 100).roundToInt()}% · ${moonAltitude.roundToInt()}°",
                        color = Color.White,
                        fontFamily = GoogleSansFlex.family,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(14.dp))
                    Slider(
                        value = progress,
                        onValueChange = {
                            progress = it
                            epoch = midnight + (it * DAY_MS).toLong()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("24 hours in 30 seconds"),
                            Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = GoogleSansFlex.family,
                            fontSize = 13.sp
                        )
                        IconButton(
                            onClick = {
                                progress = 0f
                                epoch = midnight
                            },
                            modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(Icons.Rounded.Replay, contentDescription = tr("Reset"), tint = Color.White)
                        }
                        IconButton(
                            onClick = { playing = !playing },
                            modifier = Modifier.size(48.dp).background(Color.White, CircleShape)
                        ) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) tr("Pause") else tr("Play"),
                                tint = Color(0xFF0B1119)
                            )
                        }
                    }
                }
            }
        }
    }
}
