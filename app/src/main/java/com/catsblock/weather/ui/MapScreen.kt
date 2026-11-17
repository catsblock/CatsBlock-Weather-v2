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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.components.ExpressiveLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject

private val CycloneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Cyclone",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 12f)
            curveTo(12f, 10.5f, 13.5f, 10f, 14.5f, 11f)
            curveTo(16f, 12.5f, 14.5f, 15.5f, 12f, 15.5f)
            curveTo(8.8f, 15.5f, 7f, 12.5f, 8f, 9.5f)
            curveTo(9.2f, 6f, 14f, 5f, 17f, 7.5f)
            curveTo(20.5f, 10.5f, 19.5f, 16f, 15.5f, 18.8f)
        }
    }.build()
}

internal enum class MapTimeline { Static, WeatherModel, RadarFrames }

internal enum class MapLayer(val id: String, val label: String, val icon: ImageVector, val timeline: MapTimeline = MapTimeline.Static) {
    None("none", "None (Default)", Icons.Rounded.LayersClear),
    Precipitation("precip", "Precipitation", Icons.Rounded.Umbrella, MapTimeline.RadarFrames),
    Temperature("temp", "Temperature", Icons.Rounded.Thermostat, MapTimeline.WeatherModel),
    Clouds("clouds", "Cloudiness", Icons.Rounded.Cloud, MapTimeline.WeatherModel),
    Wind("wind", "Wind speed", Icons.Rounded.Air, MapTimeline.WeatherModel),
    Pressure("pressure", "Pressure", Icons.Rounded.Speed, MapTimeline.WeatherModel),
    Humidity("humidity", "Humidity", Icons.Rounded.WaterDrop, MapTimeline.WeatherModel),
    Cyclones("cyclones", "Hurricanes & cyclones", CycloneIcon),
    OceanHeat("sst", "El Niño & ocean heat", Icons.Rounded.Waves),
    Fires("fires", "Active fires", Icons.Rounded.LocalFireDepartment),
    DewPoint("dew", "Dew point", Icons.Rounded.WaterDrop, MapTimeline.WeatherModel),
    UvIndex("uv", "UV index", Icons.Rounded.WbSunny, MapTimeline.WeatherModel),
    AirQuality("aqi", "Air quality", Icons.Rounded.Masks),
    SeaLevel("sea", "Sea level", Icons.Rounded.Water),
    Alerts("alerts", "Weather alerts", Icons.Rounded.Warning),
    Drought("drought", "Drought & dryness", Icons.Rounded.Landscape)
}

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
    var layer by remember { mutableStateOf(MapLayer.None) }
    var selectedDetail by remember { mutableStateOf<MapDetail?>(null) }
    var animatedDetail by remember { mutableStateOf<MapDetail?>(null) }
    var ready by remember { mutableStateOf(false) }
    var focusRequest by remember { mutableIntStateOf(0) }
    val bottomInset = LocalBottomInset.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetOpen by remember { mutableStateOf(false) }
    val locate = remember { Animatable(0f) }
    LaunchedEffect(layer) {
        selectedDetail = null
    }
    LaunchedEffect(selectedDetail) {
        selectedDetail?.let { animatedDetail = it }
    }
    val selectedTimeMillis = remember(layer) { System.currentTimeMillis() }

    Box(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface)) {
        NativeWeatherMap(
            city = city,
            layer = layer,
            dark = dark,
            focusRequest = focusRequest,
            radarTileTemplate = null,
            selectedTimeMillis = selectedTimeMillis,
            onPointSelected = { latitude, longitude, x, y ->
                if (layer.id in setOf("precip", "temp", "clouds", "wind", "pressure", "humidity", "dew", "uv", "aqi", "sea")) {
                    selectedDetail = MapDetail(
                        "weather-point",
                        JSONObject()
                            .put("lat", latitude)
                            .put("lon", longitude)
                            .put("layer", layer.id)
                            .put("time", selectedTimeMillis),
                        x.toDouble(),
                        y.toDouble()
                    )
                } else {
                    selectedDetail = null
                }
            },
            onMapReady = { ready = it },
            modifier = Modifier.fillMaxSize().clipToBounds()
        )

        AnimatedVisibility(
            visible = !ready,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
        ) {
            StatusPill {
                ExpressiveLoader(size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Loading map…"), style = MaterialTheme.typography.titleSmall)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = bottomInset + 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
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
                    city?.let {
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
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
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
private fun MapTimelineControl(
    layer: MapLayer,
    offsetDays: Int,
    onOffsetDays: (Int) -> Unit,
    hour: Int,
    onHour: (Int) -> Unit,
    frames: List<RadarFrame>,
    frameIndex: Int,
    onFrameIndex: (Int) -> Unit,
    playing: Boolean,
    onTogglePlaying: () -> Unit,
    selectedTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val frame = frames.getOrNull(frameIndex)
    val timestamp = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm 'UTC'")
        .withLocale(I18n.lang.locale).withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(selectedTimeMillis))
    val status = if (layer.timeline == MapTimeline.RadarFrames) {
        tr(if (frame?.observed != false) "Observed" else "Forecast")
    } else {
        tr("Forecast model")
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (layer.timeline == MapTimeline.RadarFrames) {
                    IconButton(onClick = onTogglePlaying, enabled = frames.size > 1, modifier = Modifier.size(34.dp)) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (playing) "Pause" else "Play"), Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(timestamp, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(
                        if (layer.timeline == MapTimeline.RadarFrames) "RainViewer · $status" else "Open-Meteo · $status",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Slider(
                value = if (layer.timeline == MapTimeline.RadarFrames) frameIndex.toFloat() else offsetDays.toFloat(),
                onValueChange = {
                    if (layer.timeline == MapTimeline.RadarFrames) onFrameIndex(it.toInt()) else onOffsetDays(it.toInt())
                },
                valueRange = if (layer.timeline == MapTimeline.RadarFrames) 0f..frames.lastIndex.coerceAtLeast(1).toFloat() else 0f..14f,
                steps = if (layer.timeline == MapTimeline.RadarFrames) (frames.size - 2).coerceAtLeast(0) else 13,
                enabled = layer.timeline != MapTimeline.RadarFrames || frames.size > 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                val edgeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm 'UTC'", I18n.lang.locale).withZone(java.time.ZoneOffset.UTC)
                val firstFrame = frames.firstOrNull()?.time?.let { edgeFormatter.format(java.time.Instant.ofEpochSecond(it)) }
                val lastFrame = frames.lastOrNull()?.time?.let { edgeFormatter.format(java.time.Instant.ofEpochSecond(it)) }
                Text(firstFrame ?: tr("Today"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lastFrame ?: "+14 d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (layer.timeline == MapTimeline.WeatherModel) {
                Slider(
                    value = hour.toFloat(),
                    onValueChange = { onHour(it.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text("00:00 UTC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("23:00 UTC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
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
            if (item.timeline != MapTimeline.Static) {
                val note = if (item.timeline == MapTimeline.RadarFrames) tr("Radar frames") else tr("Forecast model")
                Text(note, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
