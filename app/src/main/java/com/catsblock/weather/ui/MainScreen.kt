package com.catsblock.weather.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.City
import com.catsblock.weather.data.CitySummary
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.Day
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.aqiAdvice
import com.catsblock.weather.data.aqiStatus
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.humidityDescription
import com.catsblock.weather.data.humidityLabel
import com.catsblock.weather.data.lifeIndex
import com.catsblock.weather.data.precipLabel
import com.catsblock.weather.data.uvAdvice
import com.catsblock.weather.data.uvLevel
import com.catsblock.weather.data.windDescription
import com.catsblock.weather.sound.WeatherSoundPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

private sealed interface Sheet {
    data class DaySheet(val day: Day) : Sheet
    data object Precip : Sheet
    data object Wind : Sheet
    data object Air : Sheet
    data object Uv : Sheet
    data object Humidity : Sheet
}

private val headerShadow = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), blurRadius = 8f))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(vm: WeatherViewModel) {
    val state by vm.weather.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saved by vm.savedCities.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val bundle = (state as? WeatherState.Success)?.bundle
    val kind = bundle?.current?.kind ?: Condition.Clear
    val isDay = bundle?.current?.isDay ?: true
    val progress = (scroll.value / 700f).coerceIn(0f, 1f)

    val sound = remember { WeatherSoundPlayer() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var started by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) started = true
            if (event == Lifecycle.Event.ON_STOP) started = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sound.stop()
        }
    }
    LaunchedEffect(bundle != null, kind, settings.sounds, started) {
        if (bundle != null && settings.sounds && started) sound.play(kind) else sound.stop()
    }
    LaunchedEffect(city) {
        while (true) {
            delay(900_000)
            vm.refresh(true)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                CitiesDrawer(
                    saved = saved,
                    summaries = summaries,
                    current = city,
                    currentHasAlert = bundle?.alerts?.isNotEmpty() == true,
                    fahrenheit = settings.fahrenheit,
                    onSelect = {
                        scope.launch { drawerState.close() }
                        vm.selectCity(it)
                    },
                    onRemove = vm::removeCity,
                    onAdd = {
                        scope.launch { drawerState.close() }
                        vm.openPicker()
                    }
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            WeatherBackground(
                kind,
                isDay,
                Modifier.fillMaxSize().blur((progress * 28f).dp, BlurredEdgeTreatment.Unbounded)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = progress * 0.35f)))

            when {
                bundle != null -> WeatherDetails(
                    bundle = bundle,
                    city = city,
                    fahrenheit = settings.fahrenheit,
                    scroll = scroll
                )
                state is WeatherState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        (state as WeatherState.Error).message,
                        style = MaterialTheme.typography.titleMedium.merge(headerShadow),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.refresh() }) { Text("Retry") }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = vm::openPicker) { Text("Choose a location") }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(72.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.28f),
                    contentColor = Color.White
                )
                FilledTonalIconButton(
                    onClick = {
                        vm.loadSummaries()
                        scope.launch { drawerState.open() }
                    },
                    colors = colors
                ) { Icon(Icons.Rounded.LocationCity, "Saved cities") }
                FilledTonalIconButton(onClick = vm::openMap, colors = colors) {
                    Icon(Icons.Rounded.Map, "Radar map")
                }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = vm::openSettings, colors = colors) {
                    Icon(Icons.Rounded.Settings, "Settings")
                }
            }
        }
    }
}

@Composable
private fun CitiesDrawer(
    saved: List<City>,
    summaries: Map<String, CitySummary>,
    current: City?,
    currentHasAlert: Boolean,
    fahrenheit: Boolean,
    onSelect: (City) -> Unit,
    onRemove: (City) -> Unit,
    onAdd: () -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Saved cities", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
        saved.forEach { c ->
            val summary = summaries[c.id]
            val alert = summary?.hasAlert == true || (c.id == current?.id && currentHasAlert)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (alert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(c) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (summary != null) {
                        WeatherIcon(summary.kind, summary.isDay, Modifier.size(44.dp))
                    } else {
                        Spacer(Modifier.size(44.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            listOf(c.region, c.country).filter { it.isNotBlank() }.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (alert) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error) {
                            Icon(
                                Icons.Rounded.Warning,
                                "Weather alert",
                                Modifier.padding(4.dp).size(16.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                    }
                    if (summary != null) {
                        Text(tempText(summary.temp, fahrenheit), style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { onRemove(c) }) { Icon(Icons.Rounded.Close, "Remove ${c.name}") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add a city") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherDetails(bundle: WeatherBundle, city: City?, fahrenheit: Boolean, scroll: ScrollState) {
    val cur = bundle.current
    val nowHour = cur.now.truncatedTo(ChronoUnit.HOURS)
    val startIdx = bundle.hours.indexOfFirst { !it.time.isBefore(nowHour) }.coerceAtLeast(0)
    val next36 = bundle.hours.drop(startIdx).take(36)
    val next24 = bundle.hours.drop(startIdx).take(24)
    val today = cur.now.toLocalDate()
    val todayDay = bundle.days.firstOrNull { it.date == today } ?: bundle.days.first()
    val chartScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val screenHeight = LocalConfiguration.current.screenHeightDp

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding()
    ) {
        Spacer(Modifier.height(72.dp))
        Column(
            Modifier.fillMaxWidth().height((screenHeight * 0.6f).dp).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                city?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge.merge(headerShadow),
                color = Color.White
            )
            Text(
                tempText(cur.temp, fahrenheit),
                style = MaterialTheme.typography.displayLarge.merge(headerShadow),
                fontSize = 96.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            Text(
                "Feels like ${tempText(cur.feels, fahrenheit)}",
                style = MaterialTheme.typography.titleMedium.merge(headerShadow),
                color = Color.White
            )
            Text(
                cur.text,
                style = MaterialTheme.typography.titleLarge.merge(headerShadow),
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            WeatherIcon(cur.kind, cur.isDay, Modifier.size(150.dp))
            if (bundle.alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            bundle.alerts.first(),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HourlyListCard(next36, fahrenheit) { index ->
                scope.launch { chartScroll.animateScrollTo(with(density) { (64.dp * index).roundToPx() }) }
            }
            HourlyChartCard(next36, fahrenheit, chartScroll)
            DaysCard(bundle.days, today, fahrenheit) { sheet = Sheet.DaySheet(it) }

            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Precipitation",
                    icon = Icons.Rounded.Umbrella,
                    value = "${f1(todayDay.precipSum)} mm",
                    label = precipLabel(todayDay.precipSum),
                    description = "Accumulated today",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { sheet = Sheet.Precip }
                )
                MetricCard(
                    title = "Wind",
                    icon = Icons.Rounded.Air,
                    value = "${compassLabel(cur.windDir)} ${cur.windSpeed.toInt()} km/h",
                    label = "Speed and direction",
                    description = windDescription(cur.windSpeed),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { sheet = Sheet.Wind }
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Air quality",
                    icon = Icons.Rounded.Eco,
                    value = cur.aqi?.toInt()?.toString() ?: "--",
                    label = cur.aqi?.let { aqiStatus(it) } ?: "Unavailable",
                    description = cur.aqi?.let { aqiAdvice(it) } ?: "Air quality data is not available for this location.",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = if (cur.aqi != null) ({ sheet = Sheet.Air }) else null
                )
                MetricCard(
                    title = "UV index",
                    icon = Icons.Rounded.WbSunny,
                    value = f1(cur.uv),
                    label = uvLevel(cur.uv),
                    description = uvAdvice(cur.uv),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { sheet = Sheet.Uv }
                )
            }
            MetricCard(
                title = "Humidity",
                icon = Icons.Rounded.WaterDrop,
                value = "${cur.humidity}%",
                label = humidityLabel(cur.humidity),
                description = humidityDescription(cur.humidity),
                modifier = Modifier.fillMaxWidth(),
                onClick = { sheet = Sheet.Humidity }
            )
            SunCard(todayDay.sunrise, todayDay.sunset, cur.now)
            LifeIndexCard(
                lifeIndex(
                    temp = cur.temp,
                    feels = cur.feels,
                    humidity = cur.humidity,
                    uv = cur.uv,
                    aqi = cur.aqi,
                    wind = cur.windSpeed,
                    precip = todayDay.precipSum,
                    kind = cur.kind,
                    dayMax = todayDay.max,
                    dayMin = todayDay.min
                )
            )
        }
    }

    sheet?.let { current ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (current) {
                    is Sheet.DaySheet -> {
                        val dayHours = bundle.hours.filter { it.time.toLocalDate() == current.day.date }
                        Text(longDateLabel(current.day.date), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${current.day.kind.label(true)}  ·  ${tempText(current.day.max, fahrenheit)} / ${tempText(current.day.min, fahrenheit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = dayHours.map { it.temp.toUnit(fahrenheit) },
                            labels = dayHours.map { hourLabel(it.time) },
                            valueLabel = { "${it.toInt()}°" }
                        )
                        Spacer(Modifier.height(8.dp))
                        SimpleChart(
                            values = dayHours.map { it.precip },
                            labels = dayHours.map { "${hourLabel(it.time)}\n${it.kind.label(it.isDay)}" },
                            valueLabel = { f1(it) },
                            bar = true,
                            minY = 0.0,
                            maxY = dayHours.maxOfOrNull { it.precip }?.coerceAtLeast(1.0) ?: 1.0,
                            color = MaterialTheme.colorScheme.tertiary,
                            stepWidth = 72.dp
                        )
                        Text("Hourly precipitation (mm)", style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Precip -> {
                        Text("Precipitation, next 24 hours", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { it.precip },
                            labels = next24.map { hourLabel(it.time) },
                            valueLabel = { f1(it) },
                            bar = true,
                            minY = 0.0,
                            maxY = next24.maxOfOrNull { it.precip }?.coerceAtLeast(1.0) ?: 1.0
                        )
                        Text("Millimeters per hour", style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Wind -> {
                        Text("Wind, next 24 hours", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { it.windSpeed },
                            labels = next24.map { "${hourLabel(it.time)}\n${compassLabel(it.windDir)}" },
                            valueLabel = { it.toInt().toString() },
                            minY = 0.0,
                            maxY = next24.maxOfOrNull { it.windSpeed }?.coerceAtLeast(10.0) ?: 10.0
                        )
                        Text("Speed in km/h with direction", style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Air -> {
                        val aqiHours = next24.filter { it.aqi != null }
                        Text("Air quality, next 24 hours", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = aqiHours.map { it.aqi ?: 0.0 },
                            labels = aqiHours.map { "${hourLabel(it.time)}\n${aqiStatus(it.aqi ?: 0.0)}" },
                            valueLabel = { it.toInt().toString() },
                            minY = 0.0,
                            maxY = aqiHours.maxOfOrNull { it.aqi ?: 0.0 }?.coerceAtLeast(50.0) ?: 50.0,
                            stepWidth = 72.dp
                        )
                        Text("US AQI", style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Uv -> {
                        val uvHours = bundle.hours.filter {
                            val d = it.time.toLocalDate()
                            d == today || d == today.plusDays(1)
                        }
                        Text("UV index, today and tomorrow", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = uvHours.map { it.uv },
                            labels = uvHours.map { "${hourLabel(it.time)}\n${weekdayLabel(it.time)}" },
                            valueLabel = { f1(it) },
                            minY = 0.0,
                            maxY = uvHours.maxOfOrNull { it.uv }?.coerceAtLeast(3.0) ?: 3.0
                        )
                        Text("Hourly UV variation", style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Humidity -> {
                        Text("Humidity, next 24 hours", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { it.humidity.toDouble() },
                            labels = next24.map { hourLabel(it.time) },
                            valueLabel = { "${it.toInt()}%" },
                            minY = 0.0,
                            maxY = 100.0
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
