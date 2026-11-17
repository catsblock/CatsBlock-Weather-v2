package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

internal enum class MapTimeline { Static, RadarFrames }

internal enum class MapLayer(val id: String, val label: String, val icon: ImageVector, val timeline: MapTimeline = MapTimeline.Static) {
    None("none", "None (Default)", Icons.Rounded.LayersClear),
    Precipitation("precip", "Precipitation", Icons.Rounded.Umbrella, MapTimeline.RadarFrames)
}

private val RadarLegendColors = listOf(
    Color(0xFF00DFF2),
    Color(0xFF287BFF),
    Color(0xFF8655D8),
    Color(0xFFED5BAF),
    Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: WeatherViewModel) {
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    var layer by remember { mutableStateOf(MapLayer.Precipitation) }
    var selectedDetail by remember { mutableStateOf<MapDetail?>(null) }
    var animatedDetail by remember { mutableStateOf<MapDetail?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    val controller = remember { RadarMapController() }
    val viewport = remember { MapViewport(city?.lat ?: 20.0, city?.lon ?: 0.0, if (city != null) 6f else 3f) }
    val bottomInset = LocalBottomInset.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locate = remember { Animatable(0f) }
    val showRadar = layer == MapLayer.Precipitation

    LaunchedEffect(layer) {
        selectedDetail = null
        if (layer != MapLayer.Precipitation) controller.playing = false
    }
    LaunchedEffect(selectedDetail) {
        selectedDetail?.let { animatedDetail = it }
    }

    Box(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface)) {
        RadarTileMap(
            controller = controller,
            viewport = viewport,
            city = city,
            showRadar = showRadar,
            dark = dark,
            focusRequest = focusRequest,
            onPointSelected = { latitude, longitude, x, y ->
                val frameMillis = (controller.frame?.time ?: 0L) * 1000L
                if (showRadar && frameMillis >= System.currentTimeMillis()) {
                    selectedDetail = MapDetail(
                        "weather-point",
                        JSONObject()
                            .put("lat", latitude)
                            .put("lon", longitude)
                            .put("layer", layer.id)
                            .put("time", frameMillis),
                        x.toDouble(),
                        y.toDouble()
                    )
                } else {
                    selectedDetail = null
                }
            },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            attributionBottomPadding = bottomInset + 6.dp
        )

        Column(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showRadar) {
                RadarLegend()
                RadarStatusChip(controller)
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { viewport.zoomBy(1f) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 6.dp, bottomStart = 6.dp)
            ) {
                Icon(Icons.Rounded.Add, tr("Zoom in"))
            }
            SmallFloatingActionButton(
                onClick = { scope.launch { viewport.zoomBy(-1f) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
            ) {
                Icon(Icons.Rounded.Remove, tr("Zoom out"))
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = bottomInset + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val rotation by animateFloatAsState(
                if (sheetOpen) 180f else 0f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                label = "layers"
            )
            FloatingActionButton(
                onClick = { sheetOpen = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    Icons.Rounded.Layers,
                    tr("Map layers"),
                    Modifier.graphicsLayer { rotationY = rotation }
                )
            }
            SmallFloatingActionButton(
                onClick = {
                    if (city != null) {
                        focusRequest++
                        scope.launch {
                            locate.snapTo(0f)
                            locate.animateTo(1f, tween(700))
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    tr("My location"),
                    Modifier.graphicsLayer {
                        val t = locate.value
                        val s = 1f + 0.3f * kotlin.math.sin(t * Math.PI).toFloat()
                        scaleX = s
                        scaleY = s
                        rotationZ = 90f * t
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showRadar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(start = 88.dp, end = 12.dp, bottom = bottomInset + 12.dp),
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.96f),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.98f)
        ) {
            RadarPlaybackBar(controller)
        }
    }

    AnimatedVisibility(
        visible = selectedDetail?.type == "weather-point",
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.96f)
    ) {
        animatedDetail?.let { detail ->
            if (detail.type == "weather-point") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val popupWidth = (maxWidth - 24.dp).coerceAtMost(340.dp).coerceAtLeast(220.dp)
                    val popupHeight = (maxHeight - 100.dp).coerceAtMost(360.dp).coerceAtLeast(160.dp)
                    val anchorX = detail.anchorX.toFloat().dp
                    val anchorY = detail.anchorY.toFloat().dp
                    val left = (anchorX - popupWidth / 2f).coerceIn(12.dp, (maxWidth - popupWidth - 12.dp).coerceAtLeast(12.dp))
                    val topLimit = (maxHeight - popupHeight - 12.dp).coerceAtLeast(72.dp)
                    val preferredTop = if (anchorY + 14.dp <= topLimit) anchorY + 14.dp else anchorY - popupHeight - 14.dp
                    val top = preferredTop.coerceIn(72.dp, topLimit)
                    MapPointPopup(
                        detail,
                        settings.fahrenheit,
                        settings.windUnit,
                        settings.pressureUnit,
                        { selectedDetail = null },
                        Modifier.offset(left, top).width(popupWidth).heightIn(max = popupHeight)
                    )
                }
            } else {
                MapDetailSheet(detail, settings.fahrenheit, settings.windUnit, settings.pressureUnit) { selectedDetail = null }
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    tr("Map layers"),
                    Modifier.padding(start = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                MapLayer.entries.forEach { item ->
                    LayerRow(item, item == layer) {
                        layer = item
                        selectedDetail = null
                        scope.launch {
                            sheetState.hide()
                            sheetOpen = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarLegend() {
    Surface(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 8.dp, bottomEnd = 18.dp, bottomStart = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.widthIn(min = 152.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(tr("Precipitation"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(RadarLegendColors))
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("15 dBZ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("65 dBZ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RadarStatusChip(controller: RadarMapController) {
    when (controller.radarState) {
        RadarDataState.Loading -> StatusSurface(null) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(tr("Loading radar…"), style = MaterialTheme.typography.labelLarge)
        }
        RadarDataState.Unavailable -> StatusSurface({ controller.retryAll() }) {
            Text(
                "${tr("Radar data unavailable")} · ${tr("Retry")}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
        RadarDataState.Ready -> Unit
    }
}

@Composable
private fun StatusSurface(onClick: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 3.dp,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun RadarPlaybackBar(controller: RadarMapController, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val frame = controller.frame
    val frames = controller.frames
    val timeFormatter = remember(I18n.lang) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(I18n.lang.locale).withZone(ZoneId.systemDefault())
    }
    val clock = frame?.let { timeFormatter.format(Instant.ofEpochSecond(it.time)) } ?: "--:--"
    val status = frame?.let { "${tr(if (it.observed) "Observed" else "Forecast")} · ${relativeFrameLabel(it.time)}" } ?: tr("Unavailable")
    val corner by animateDpAsState(if (controller.playing) 14.dp else 22.dp, spring(0.6f, Spring.StiffnessMediumLow), label = "playCorner")
    val progress by animateFloatAsState(
        if (frames.size > 1) controller.index.toFloat() / (frames.size - 1) else 0f,
        tween(300),
        label = "frameProgress"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 14.dp, bottomEnd = 28.dp, bottomStart = 14.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = { controller.playing = !controller.playing },
                    enabled = frames.size > 1,
                    shape = RoundedCornerShape(corner),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (controller.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        tr(if (controller.playing) "Pause" else "Play"),
                        Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(clock, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text("RainViewer", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = scheme.primary,
                trackColor = scheme.outlineVariant
            )
        }
    }
}

private fun relativeFrameLabel(epochSeconds: Long): String {
    val minutes = ((epochSeconds - System.currentTimeMillis() / 1000L) / 60L).toInt()
    return when {
        abs(minutes) <= 1 -> tr("Now")
        minutes < 0 && -minutes < 60 -> "−${-minutes}m"
        minutes < 0 -> "−${-minutes / 60}h ${-minutes % 60}m"
        minutes < 60 -> "+${minutes}m"
        else -> "+${minutes / 60}h ${minutes % 60}m"
    }
}

@Composable
private fun LayerRow(item: MapLayer, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 32.dp else 18.dp, spring(0.7f, Spring.StiffnessMediumLow), label = "corner")
    val container by animateColorAsState(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh, tween(260), label = "container")
    val badge by animateColorAsState(if (selected) scheme.secondary else scheme.surfaceContainerHighest, tween(260), label = "badge")
    val badgeInk by animateColorAsState(if (selected) scheme.onSecondary else scheme.onSurfaceVariant, tween(260), label = "badgeInk")
    val pop by animateFloatAsState(if (selected) 1f else 0.9f, spring(0.4f, Spring.StiffnessMedium), label = "pop")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
                .clip(CircleShape)
                .background(badge),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, Modifier.size(22.dp), tint = badgeInk)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr(item.label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface
            )
            if (item.timeline == MapTimeline.RadarFrames) {
                Text(tr("Radar frames"), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(0.45f, Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(24.dp), tint = scheme.onSecondaryContainer)
        }
    }
}
