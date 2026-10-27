package com.catsblock.weather.ui

import androidx.compose.ui.platform.LocalContext
import com.catsblock.weather.data.NetworkPolicy
import com.catsblock.weather.data.HubAlert
import com.catsblock.weather.data.AlertHub
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.catsblock.weather.data.AppSettings
import kotlin.math.abs
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.catsblock.weather.data.buildAlert
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.humidityDescription
import com.catsblock.weather.data.humidityLabel
import com.catsblock.weather.data.isWet
import com.catsblock.weather.data.lifeIndex
import com.catsblock.weather.data.precipToday
import com.catsblock.weather.data.uvAdvice
import com.catsblock.weather.data.windDescription
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.sound.WeatherSoundPlayer
import com.catsblock.weather.ui.glass.EntranceCounter
import com.catsblock.weather.ui.glass.LocalEntrance
import com.catsblock.weather.ui.glass.LocalHazeState
import com.catsblock.weather.ui.glass.LocalRainIntensity
import com.catsblock.weather.ui.glass.rainIntensity
import com.catsblock.weather.ui.glass.hazeBackdropSource
import com.catsblock.weather.ui.particles.ParticleOverlay
import com.catsblock.weather.ui.particles.ParticleWeather
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private sealed interface Sheet {
    data class DaySheet(val day: Day) : Sheet
    data object Days : Sheet
    data object Precip : Sheet
    data object Wind : Sheet
    data object Air : Sheet
    data object Uv : Sheet
    data object Humidity : Sheet
    data object Pressure : Sheet
    data object Visibility : Sheet
    data object Cloud : Sheet
    data object Moon : Sheet
}

private val textShadow = TextStyle(
    shadow = Shadow(Color(0x66000000), offset = Offset(0f, 2f), blurRadius = 10f)
)

private val snowKinds = setOf(Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Sleet)

fun beaufort(kmh: Double): Int = when {
    kmh < 1 -> 0
    kmh < 6 -> 1
    kmh < 12 -> 2
    kmh < 20 -> 3
    kmh < 29 -> 4
    kmh < 39 -> 5
    kmh < 50 -> 6
    kmh < 75 -> 7
    kmh < 89 -> 8
    kmh < 103 -> 9
    kmh < 118 -> 10
    else -> 11
}

fun windShort(kmh: Double): String = tr(
    when {
        kmh < 1 -> "Calm air."
        kmh < 12 -> "A light breeze, very comfortable."
        kmh < 29 -> "A pleasant breeze."
        kmh < 50 -> "Strong wind, hold on to your hat."
        kmh < 75 -> "Very strong wind. Be careful outside."
        else -> "Dangerous winds. Stay indoors."
    }
)

fun aqiShort(v: Double): String = tr(
    when {
        v <= 50.0 -> "Air quality is ideal."
        v <= 100.0 -> "Air quality is acceptable."
        v <= 150.0 -> "Unhealthy for sensitive groups."
        v <= 200.0 -> "Unhealthy air quality."
        else -> "Hazardous air quality."
    }
)

fun uvShort(v: Double): String = tr(
    when {
        v < 3.0 -> "UV is low right now."
        v < 5.0 -> "Wear SPF 30 if outside long."
        v < 7.0 -> "SPF 30 and sunglasses advised."
        v < 10.0 -> "SPF 50+ and a hat advised."
        else -> "Avoid direct midday sun."
    }
)

fun pressureShort(hpa: Double): String = tr(
    when {
        hpa < 1000.0 -> "Low pressure. Unsettled weather is possible."
        hpa < 1020.0 -> "Normal pressure."
        else -> "High pressure. Stable weather is likely."
    }
)

fun visibilityShort(km: Double): String = tr(
    when {
        km < 1.0 -> "Very poor visibility. Drive carefully."
        km < 4.0 -> "Poor visibility."
        km < 10.0 -> "Moderate visibility."
        else -> "Excellent visibility."
    }
)

private fun visibilityText(km: Double): String = if (km >= 10.0) km.roundToInt().toString() else f1(km)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: WeatherViewModel) {
    val state by vm.weather.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saved by vm.savedCities.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    var citiesOpen by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scheme = MaterialTheme.colorScheme
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val scope = rememberCoroutineScope()
    val elastic = remember {
        ElasticScrollState(
            scope = scope,
            maxPull = with(density) { 240.dp.toPx() },
            threshold = with(density) { 72.dp.toPx() },
            holdOffset = with(density) { 56.dp.toPx() },
            onRefresh = { vm.pullRefresh() }
        )
    }
    LaunchedEffect(refreshing) { if (!refreshing) elastic.endRefresh() }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.notices.collect {
            notice = it
            delay(2400)
            notice = null
        }
    }
    var swipe by remember { mutableFloatStateOf(0f) }
    val cityIndex = saved.indexOfFirst { it.id == city?.id }
    val nav by rememberUpdatedState(Pair(cityIndex > 0, cityIndex >= 0 && cityIndex < saved.lastIndex))
    LaunchedEffect(city?.id) { scroll.scrollTo(0) }
    val hazeState = remember { HazeState() }
    var mode by remember { mutableStateOf(DashboardMode.Temperature) }

    val bundle = (state as? WeatherState.Success)?.bundle
    val kind = bundle?.current?.kind ?: Condition.Clear
    val isDay = bundle?.current?.isDay ?: true
    val currentSummary = remember(bundle) {
        bundle?.let { b ->
            val today = b.current.now.toLocalDate()
            val d = b.days.firstOrNull { it.date == today } ?: b.days.first()
            CitySummary(b.current.kind, b.current.isDay, b.current.temp, d.min, d.max, b.current.now.toLocalTime())
        }
    }
    val progress = (scroll.value / 700f).coerceIn(0f, 1f)
    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 112.dp

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

    run {
        CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeBackdropSource(hazeState)) {
                WeatherBackground(
                    kind,
                    isDay,
                    Modifier.fillMaxSize().blur((progress * 30f).dp, BlurredEdgeTreatment.Unbounded),
                    bundle?.current?.now?.toLocalTime(),
                    bundle?.current?.cloud ?: -1,
                    bundle?.current?.windSpeed ?: 0.0,
                    bundle?.current?.now?.dayOfYear ?: 0
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = progress * 0.32f)))
            Box(Modifier.fillMaxSize().background(scheme.primary.copy(alpha = progress * 0.14f)))

            CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(elastic.connection)
                    .pointerInput(Unit) {
                        var lastDx = 0f
                        suspend fun settle(velocity: Float) {
                            val threshold = widthPx * 0.25f
                            val dir = when {
                                swipe < -threshold || (swipe < -widthPx * 0.08f && velocity < -20f) -> 1
                                swipe > threshold || (swipe > widthPx * 0.08f && velocity > 20f) -> -1
                                else -> 0
                            }
                            val allowed = (dir == 1 && nav.second) || (dir == -1 && nav.first)
                            if (allowed) {
                                animate(swipe, -dir * widthPx, animationSpec = tween(180, easing = FastOutLinearInEasing)) { v, _ -> swipe = v }
                                vm.selectAdjacent(dir)
                                swipe = dir * widthPx
                                animate(swipe, 0f, animationSpec = spring(0.85f, Spring.StiffnessMedium)) { v, _ -> swipe = v }
                            } else {
                                animate(swipe, 0f, animationSpec = spring(0.7f, Spring.StiffnessMediumLow)) { v, _ -> swipe = v }
                            }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { lastDx = 0f },
                            onDragEnd = { scope.launch { settle(lastDx) } },
                            onDragCancel = { scope.launch { settle(0f) } },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                lastDx = dx
                                val allowed = if (swipe + dx < 0f) nav.second else nav.first
                                swipe += if (allowed) dx else dx * 0.25f
                            }
                        )
                    }
            ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = swipe
                        alpha = 1f - (abs(swipe) / widthPx).coerceIn(0f, 1f) * 0.55f
                    }
            ) {
            when {
                bundle != null -> CompositionLocalProvider(
                    LocalUnits provides UnitPrefs(settings.windUnit, settings.pressureUnit)
                ) {
                    WeatherDetails(
                        bundle = bundle,
                        city = city,
                        settings = settings,
                        scroll = scroll,
                        headerHeight = headerHeight,
                        mode = mode,
                        overscroll = { elastic.offset },
                        onLiveMoon = vm::openLiveMoon
                    )
                }
                state is WeatherState.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        tr((state as WeatherState.Error).message),
                        style = MaterialTheme.typography.titleMedium.merge(textShadow),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.refresh() }) { Text(tr("Retry")) }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = vm::openPicker) { Text(tr("Choose a location")) }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(48.dp), color = Color.White)
                }
            }
            }
            }
            }

            PullIndicator(elastic, refreshing, headerHeight)

            ParticleOverlay(
                when {
                    bundle == null -> ParticleWeather.None
                    kind in snowKinds -> ParticleWeather.Snow
                    else -> ParticleWeather.None
                },
                windSpeedKmh = bundle?.current?.windSpeed ?: 0.0,
                windDirDeg = bundle?.current?.windDir ?: 0.0
            )

            HeaderBar(
                mode = mode,
                onMode = { mode = it },
                name = city?.name.orEmpty(),
                temp = bundle?.let { tempText(it.current.temp, settings.fahrenheit) },
                progress = progress,
                dots = saved.size.coerceIn(1, 6),
                current = saved.indexOfFirst { it.id == city?.id }.coerceAtLeast(0),
                onCities = {
                    vm.loadSummaries()
                    citiesOpen = true
                },
                onSettings = vm::openSettings
            )

            AnimatedVisibility(
                visible = notice != null,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                Surface(shape = CircleShape, color = Color(0xCC1C1C1E)) {
                    Text(
                        tr(notice ?: ""),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            CitiesOverlay(
                visible = citiesOpen,
                saved = saved,
                selectedId = city?.id,
                current = currentSummary,
                summaries = summaries,
                fahrenheit = settings.fahrenheit,
                onSelect = {
                    citiesOpen = false
                    vm.selectCity(it)
                },
                onRemove = vm::removeCity,
                onAdd = {
                    citiesOpen = false
                    vm.openPicker()
                },
                onClose = { citiesOpen = false }
            )
        }
        }
    }
}

@Composable
private fun PullIndicator(elastic: ElasticScrollState, refreshing: Boolean, headerHeight: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = headerHeight + 8.dp)
            .graphicsLayer {
                val pull = elastic.offset
                alpha = if (refreshing) 1f else (pull / 90f).coerceIn(0f, 1f)
                translationY = (pull * 0.4f).coerceAtLeast(0f)
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp), tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            tr(if (refreshing) "Updating" else "Update"),
            style = MaterialTheme.typography.labelLarge.merge(textShadow),
            color = Color.White
        )
    }
}

@Composable
private fun HeaderBar(
    mode: DashboardMode,
    onMode: (DashboardMode) -> Unit,
    name: String,
    temp: String?,
    progress: Float,
    dots: Int,
    current: Int,
    onCities: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(
                if (temp != null && progress > 0.55f) "$name $temp" else name,
                style = MaterialTheme.typography.headlineSmall.merge(textShadow),
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NearMe, null, Modifier.size(14.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                repeat(dots) { i ->
                    Box(
                        Modifier
                            .padding(end = 5.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.4f))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ModeSelector(mode, onMode)
        }
        IconButton(onClick = onCities) { Icon(Icons.Rounded.Menu, tr("Saved cities"), tint = Color.White) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, tr("Settings"), tint = Color.White) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherDetails(
    bundle: WeatherBundle,
    city: City?,
    settings: AppSettings,
    scroll: ScrollState,
    headerHeight: Dp,
    mode: DashboardMode,
    overscroll: () -> Float,
    onLiveMoon: () -> Unit
) {
    val fahrenheit = settings.fahrenheit
    val density = LocalDensity.current
    val cur = bundle.current
    val nowHour = cur.now.truncatedTo(ChronoUnit.HOURS)
    val startIdx = bundle.hours.indexOfFirst { !it.time.isBefore(nowHour) }.coerceAtLeast(0)
    val next36 = bundle.hours.drop(startIdx).take(36)
    val next24 = bundle.hours.drop(startIdx).take(24)
    val today = cur.now.toLocalDate()
    val upcoming = bundle.days.filter { !it.date.isBefore(today) }.take(7)
    val todayDay = upcoming.firstOrNull() ?: bundle.days.first()
    val chipDays = upcoming.filter { d -> next36.any { it.time.toLocalDate() == d.date } }
    val chartScroll = rememberScrollState()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val hubContext = LocalContext.current
    var hubAlerts by remember(city?.id) { mutableStateOf(emptyList<HubAlert>()) }
    var hubSheet by remember { mutableStateOf(false) }
    LaunchedEffect(city?.id, cur.now, settings.language) {
        val target = city
        if (target != null && NetworkPolicy.foregroundAllowed(hubContext, settings)) {
            hubAlerts = runCatching { AlertHub.fetch(target.lat, target.lon) }.getOrDefault(hubAlerts)
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val heroHeight = ((screenHeight * (if (settings.compactHero) 0.46f else 0.66f)).dp - headerHeight)
        .coerceAtLeast(if (settings.compactHero) 240.dp else 320.dp)
    val alert = remember(bundle, fahrenheit) { buildAlert(bundle, fahrenheit) }
    val baseScheme = MaterialTheme.colorScheme
    val glassScheme = remember(baseScheme) {
        baseScheme.copy(
            primary = Color(0xFFCDE6FF),
            onPrimary = Color(0xFF0B1B2E),
            secondary = Color(0xFF9FD8FF),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xCCFFFFFF),
            outline = Color(0x99FFFFFF),
            outlineVariant = Color(0x44FFFFFF)
        )
    }
    val pressureLow = next24.minOfOrNull { it.pressure } ?: cur.pressure
    val pressureHigh = next24.maxOfOrNull { it.pressure } ?: cur.pressure
    val visibilityKm = cur.visibility / 1000.0
    val rainingNow = cur.kind.isWet
    val entrance = remember(city?.id) { EntranceCounter() }
    val heroIn = remember { Animatable(0f) }
    LaunchedEffect(city?.id) {
        heroIn.snapTo(0f)
        heroIn.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val snowingNow = cur.kind in snowKinds

    CompositionLocalProvider(LocalEdgeFadeInset provides with(density) { headerHeight.toPx() }) {
    Column(Modifier.fillMaxSize().padding(top = headerHeight).verticalScroll(scroll)) {
    Column(Modifier.graphicsLayer { translationY = overscroll() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    alpha = heroIn.value
                    translationY = (1f - heroIn.value) * 28.dp.toPx()
                }
                .scrollEdgeFade()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                tempText(cur.temp, fahrenheit),
                style = MaterialTheme.typography.displayLarge.merge(textShadow),
                fontSize = 150.sp,
                lineHeight = 160.sp,
                fontWeight = FontWeight.W200,
                color = Color.White
            )
            Text(
                cur.kind.label(cur.isDay),
                style = MaterialTheme.typography.titleLarge.merge(textShadow),
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${tr("Feels like")} ${tempText(cur.feels, fahrenheit)}    ${tr("Wind")} ${compassLabel(cur.windDir)} ${beaufort(cur.windSpeed)}",
                style = MaterialTheme.typography.bodyMedium.merge(textShadow),
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        CompositionLocalProvider(
            LocalGlass provides true,
            LocalEntrance provides entrance,
            LocalRainIntensity provides rainIntensity(cur.kind)
        ) {
            MaterialTheme(colorScheme = glassScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
                Column(
                    Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    alert?.let { AlertCard(it) }
                    if (bundle.alerts.isNotEmpty()) GovAlertCard(bundle.alerts, bundle.utcOffsetSeconds)
                    if (settings.showPrecipitation && bundle.rain.isNotEmpty()) PrecipitationCard(bundle)
                    HoursCard(next36, chipDays, today, fahrenheit, chartScroll)
                    if (hubAlerts.isNotEmpty()) HubAlertCard(hubAlerts, { hubSheet = true })
                    DaysCard(
                        days = upcoming,
                        today = today,
                        fahrenheit = fahrenheit,
                        onDayClick = { sheet = Sheet.DaySheet(it) },
                        onMore = { sheet = Sheet.Days }
                    )
                    CardsDashboard(bundle, city, todayDay, mode) { key ->
                        sheet = when (key) {
                            "Precip" -> Sheet.Precip
                            "Wind" -> Sheet.Wind
                            "Air" -> Sheet.Air
                            "Uv" -> Sheet.Uv
                            "Humidity" -> Sheet.Humidity
                            "Pressure" -> Sheet.Pressure
                            "Visibility" -> Sheet.Visibility
                            "Cloud" -> Sheet.Cloud
                            else -> Sheet.Moon
                        }
                    }
                    if (settings.showLifeIndex) LifeIndexCard(
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
        }
    }
    }
    }

    if (hubSheet && hubAlerts.isNotEmpty()) HubAlertsSheet(hubAlerts) { hubSheet = false }

    (sheet as? Sheet.DaySheet)?.let { current ->
        DayDetailSheet(current.day, bundle.hours, fahrenheit, today, cur.now) { sheet = null }
    }

    if (sheet is Sheet.Moon && city != null) {
        MoonCalendarSheet(city, bundle.utcOffsetSeconds, cur.now, onLiveMoon) { sheet = null }
    }

    if (sheet is Sheet.Cloud) {
        CardDetailHistoryScreen(
            title = tr("Cloud cover"),
            days = remember(bundle) { cloudHistory(bundle.hours, today) },
            today = today,
            bandLabel = ::cloudBandLabel,
            advice = ::cloudAdvice,
            onBack = { sheet = null }
        )
    }

    sheet?.takeIf { it !is Sheet.DaySheet && it !is Sheet.Moon && it !is Sheet.Cloud }?.let { current ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (current) {
                    is Sheet.DaySheet -> Unit
                    Sheet.Moon -> Unit
                    Sheet.Cloud -> Unit
                    Sheet.Days -> {
                        Text(tr("Next 7 days"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        upcoming.forEach { d ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { sheet = Sheet.DaySheet(d) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dayLabel(d.date, today), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        dateLabel(d.date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                WeatherIcon(d.kind, true, Modifier.size(36.dp))
                                Text(
                                    if (d.rainChance >= 30) "${d.rainChance}%" else "",
                                    modifier = Modifier.width(52.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "${tempText(d.max, fahrenheit)} / ${tempText(d.min, fahrenheit)}",
                                    modifier = Modifier.width(88.dp),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                    Sheet.Precip -> {
                        Text(tr("Precipitation, next 24 hours"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { it.precip },
                            labels = next24.map { hourLabel(it.time) },
                            valueLabel = { f1(it) },
                            bar = true,
                            minY = 0.0,
                            maxY = next24.maxOfOrNull { it.precip }?.coerceAtLeast(1.0) ?: 1.0
                        )
                        Text(tr("Millimeters per hour"), style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Wind -> {
                        Text(tr("Wind, next 24 hours"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            windDescription(cur.windSpeed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { windValue(it.windSpeed, settings.windUnit) },
                            labels = next24.map { "${hourLabel(it.time)}\n${compassLabel(it.windDir)}" },
                            valueLabel = { it.roundToInt().toString() },
                            minY = 0.0,
                            maxY = windValue(next24.maxOfOrNull { it.windSpeed }?.coerceAtLeast(10.0) ?: 10.0, settings.windUnit)
                        )
                        Text(windCaption(settings.windUnit), style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Air -> {
                        val aqiHours = next24.filter { it.aqi != null }
                        Text(tr("Air quality, next 24 hours"), style = MaterialTheme.typography.titleLarge)
                        cur.aqi?.let {
                            Text(
                                aqiAdvice(it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = aqiHours.map { it.aqi ?: 0.0 },
                            labels = aqiHours.map {
                                "${hourLabel(it.time)}\n${com.catsblock.weather.data.aqiStatus(it.aqi ?: 0.0)}"
                            },
                            valueLabel = { it.toInt().toString() },
                            minY = 0.0,
                            maxY = aqiHours.maxOfOrNull { it.aqi ?: 0.0 }?.coerceAtLeast(50.0) ?: 50.0,
                            stepWidth = 72.dp
                        )
                        Text(tr("US AQI"), style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Uv -> {
                        val uvHours = bundle.hours.filter {
                            val d = it.time.toLocalDate()
                            d == today || d == today.plusDays(1)
                        }
                        Text(tr("UV index, today and tomorrow"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            uvAdvice(cur.uv),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = uvHours.map { it.uv },
                            labels = uvHours.map { "${hourLabel(it.time)}\n${weekdayLabel(it.time)}" },
                            valueLabel = { f1(it) },
                            minY = 0.0,
                            maxY = uvHours.maxOfOrNull { it.uv }?.coerceAtLeast(3.0) ?: 3.0
                        )
                        Text(tr("Hourly UV variation"), style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Humidity -> {
                        Text(tr("Humidity, next 24 hours"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            humidityDescription(cur.humidity),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { it.humidity.toDouble() },
                            labels = next24.map { hourLabel(it.time) },
                            valueLabel = { "${it.toInt()}%" },
                            minY = 0.0,
                            maxY = 100.0
                        )
                    }
                    Sheet.Pressure -> {
                        Text(tr("Pressure, next 24 hours"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            pressureShort(cur.pressure),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { pressureValue(it.pressure, settings.pressureUnit) },
                            labels = next24.map { hourLabel(it.time) },
                            valueLabel = { pressureFormat(it, settings.pressureUnit) },
                            minY = pressureValue(pressureLow - 2.0, settings.pressureUnit),
                            maxY = pressureValue(pressureHigh + 2.0, settings.pressureUnit)
                        )
                        Text(pressureCaption(settings.pressureUnit), style = MaterialTheme.typography.labelMedium)
                    }
                    Sheet.Visibility -> {
                        Text(tr("Visibility, next 24 hours"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            visibilityShort(visibilityKm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SimpleChart(
                            values = next24.map { it.visibility / 1000.0 },
                            labels = next24.map { hourLabel(it.time) },
                            valueLabel = { visibilityText(it) },
                            minY = 0.0,
                            maxY = (next24.maxOfOrNull { it.visibility / 1000.0 } ?: 10.0).coerceAtLeast(1.0)
                        )
                        Text(tr("Kilometers"), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
