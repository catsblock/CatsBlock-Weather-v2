package com.catsblock.weather.ui.alerts

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.R
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.ui.WeatherState
import com.catsblock.weather.ui.WeatherViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private typealias Updater = ((AlertState) -> AlertState) -> Unit

@Composable
fun AlertSettingsScreen(vm: WeatherViewModel, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AlertSettingsContent(vm, onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertSettingsContent(vm: WeatherViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AlertStore(ctx.applicationContext) }
    val s by store.flow.collectAsStateWithLifecycle(initialValue = AlertState())
    val settings by vm.settings.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val bundle = (weather as? WeatherState.Success)?.bundle
    val update: Updater = { block -> scope.launch { store.update(block) } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.alerts_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.alerts_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClothingCard(s, bundle, settings.fahrenheit, update)
            UvCard(s, bundle, update)
            PollenCard(s, update)
            FrostCard(s, bundle, settings.fahrenheit, update)
            BriefingCard(s, update)
            PressureCard(s, bundle, update)
            AqiCard(s, bundle, update)
            OfficialCard(s, update)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun temperatureText(celsius: Double, fahrenheit: Boolean): String =
    "${(if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius).roundToInt()}°"

private fun hourLabel(hour: Hour): String = "%02d:00".format(hour.time.hour)

private fun axisLabels(hours: List<Hour>): List<String> =
    if (hours.size < 2) emptyList() else listOf(hourLabel(hours.first()), hourLabel(hours[hours.size / 2]), hourLabel(hours.last()))

private fun todayHours(bundle: WeatherBundle?): List<Hour> {
    if (bundle == null) return emptyList()
    val date = bundle.current.now.toLocalDate()
    val same = bundle.hours.filter { it.time.toLocalDate() == date }
    return if (same.size >= 6) same else bundle.hours.take(24)
}

private fun nightHours(bundle: WeatherBundle?): List<Hour> {
    if (bundle == null) return emptyList()
    fun night(h: Hour) = h.time.hour >= 20 || h.time.hour <= 7
    return bundle.hours.dropWhile { !night(it) }.takeWhile { night(it) }
}

@StringRes
private fun clothingTags(
    rainy: Boolean,
    mean: Double,
    uvMax: Double,
    thermal: Int
): List<Int> {
    val shift = when (thermal) {
        0 -> -3.0
        2 -> 3.0
        else -> 0.0
    }
    val effective = mean + shift
    val tags = mutableListOf<Int>()
    if (rainy) tags += R.string.clothing_tag_waterproof
    tags += when {
        effective <= 2.0 -> R.string.clothing_tag_heavy
        effective <= 10.0 -> R.string.clothing_tag_warm
        effective <= 17.0 -> R.string.clothing_tag_sweater
        effective >= 18.0 -> R.string.clothing_tag_light
        else -> R.string.clothing_tag_layers
    }
    if (uvMax >= 6.0) tags += R.string.clothing_tag_sun
    return tags
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClothingCard(s: AlertState, bundle: WeatherBundle?, fahrenheit: Boolean, update: Updater) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AlertCard(
        icon = Icons.Rounded.Checkroom,
        title = stringResource(R.string.clothing_title),
        summary = if (s.clothingOn) stringResource(R.string.clothing_summary_on, formatMinutes(s.clothingTime)) else stringResource(R.string.clothing_summary_off),
        enabled = s.clothingOn,
        onToggle = { v -> update { it.copy(clothingOn = v) } }
    ) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            listOf(R.string.clothing_tab_today, R.string.clothing_tab_tomorrow).forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
            }
        }
        val day = bundle?.let { b ->
            val base = b.current.now.toLocalDate()
            b.days.firstOrNull { it.date == base.plusDays(tab.toLong()) }
        }
        if (day == null) {
            Text(
                stringResource(R.string.alerts_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                stringResource(
                    R.string.clothing_weather_summary,
                    temperatureText(day.max, fahrenheit),
                    temperatureText(day.min, fahrenheit),
                    day.rainChance
                ),
                style = MaterialTheme.typography.titleMedium
            )
            GroupTitle(stringResource(R.string.clothing_recommended))
            val rainy = day.rainChance >= 40 || day.precipSum >= 1.0
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                clothingTags(rainy, (day.max + day.min) / 2.0, day.uvMax, s.thermal).forEach { tag ->
                    SuggestionChip(onClick = {}, label = { Text(stringResource(tag)) })
                }
            }
        }
        TimeRow(stringResource(R.string.alerts_alert_time), s.clothingTime) { v -> update { it.copy(clothingTime = v) } }
        GroupTitle(stringResource(R.string.clothing_thermal))
        ChoiceRow(
            listOf(
                stringResource(R.string.clothing_thermal_cold),
                stringResource(R.string.clothing_thermal_neutral),
                stringResource(R.string.clothing_thermal_warm)
            ),
            s.thermal
        ) { v -> update { it.copy(thermal = v) } }
        LocationField(Icons.Rounded.Home, stringResource(R.string.clothing_home), s.homeLocation) { v -> update { it.copy(homeLocation = v) } }
        LocationField(Icons.Rounded.Work, stringResource(R.string.clothing_work), s.workLocation) { v -> update { it.copy(workLocation = v) } }
        GroupTitle(stringResource(R.string.clothing_hours))
        TimeRow(stringResource(R.string.clothing_hours_start), s.workStart) { v -> update { it.copy(workStart = v) } }
        TimeRow(stringResource(R.string.clothing_hours_end), s.workEnd) { v -> update { it.copy(workEnd = v) } }
        GroupTitle(stringResource(R.string.clothing_commute))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Commute.entries.forEach { mode ->
                val (icon, label) = when (mode) {
                    Commute.Walk -> Icons.Rounded.DirectionsWalk to R.string.commute_walk
                    Commute.Bike -> Icons.Rounded.DirectionsBike to R.string.commute_bike
                    Commute.Car -> Icons.Rounded.DirectionsCar to R.string.commute_car
                    Commute.Transit -> Icons.Rounded.DirectionsBus to R.string.commute_transit
                }
                FilterChip(
                    selected = s.commute == mode,
                    onClick = { update { it.copy(commute = mode) } },
                    label = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
private fun LocationField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun uvLevelName(value: Int): String = stringResource(
    when {
        value <= 2 -> R.string.uv_low
        value <= 5 -> R.string.uv_moderate
        value <= 7 -> R.string.uv_high
        value <= 10 -> R.string.uv_very_high
        else -> R.string.uv_extreme
    }
)

@Composable
private fun UvCard(s: AlertState, bundle: WeatherBundle?, update: Updater) {
    val hours = todayHours(bundle)
    AlertCard(
        icon = Icons.Rounded.WbSunny,
        title = stringResource(R.string.uv_title),
        summary = if (s.uvOn) stringResource(R.string.uv_summary_on, s.uvThreshold, formatMinutes(s.uvTime)) else stringResource(R.string.uv_summary_off),
        enabled = s.uvOn,
        onToggle = { v -> update { it.copy(uvOn = v) } }
    ) {
        TimeRow(stringResource(R.string.alerts_alert_time), s.uvTime) { v -> update { it.copy(uvTime = v) } }
        GroupTitle(stringResource(R.string.alerts_active_months))
        MonthChips(s.uvMonths) { v -> update { it.copy(uvMonths = v) } }
        GroupTitle(stringResource(R.string.uv_graph))
        LineChart(
            values = hours.map { it.uv.toFloat() },
            xLabels = axisLabels(hours),
            thresholdValue = s.uvThreshold.toFloat(),
            thresholdLabel = "UV ${s.uvThreshold}"
        )
        StepSlider(
            label = stringResource(R.string.uv_threshold),
            value = s.uvThreshold,
            range = 1..11,
            valueText = { stringResource(R.string.uv_threshold_value, it, uvLevelName(it)) }
        ) { v -> update { it.copy(uvThreshold = v) } }
    }
}

@Composable
private fun sensitivityName(level: Int): String = stringResource(
    when (level) {
        0 -> R.string.sensitivity_low
        1 -> R.string.sensitivity_medium
        else -> R.string.sensitivity_high
    }
)

@Composable
private fun PollenCard(s: AlertState, update: Updater) {
    AlertCard(
        icon = Icons.Rounded.Grass,
        title = stringResource(R.string.pollen_title),
        summary = if (s.pollenOn) stringResource(R.string.pollen_summary_on, sensitivityName(s.pollenSensitivity), formatMinutes(s.pollenTime)) else stringResource(R.string.pollen_summary_off),
        enabled = s.pollenOn,
        onToggle = { v -> update { it.copy(pollenOn = v) } }
    ) {
        InfoBanner(stringResource(R.string.pollen_availability))
        TimeRow(stringResource(R.string.alerts_alert_time), s.pollenTime) { v -> update { it.copy(pollenTime = v) } }
        ToggleRow(stringResource(R.string.pollen_pill), stringResource(R.string.pollen_pill_desc), s.pillOn) { v -> update { it.copy(pillOn = v) } }
        AnimatedVisibility(s.pillOn) {
            TimeRow(stringResource(R.string.pollen_pill_time), s.pillTime) { v -> update { it.copy(pillTime = v) } }
        }
        GroupTitle(stringResource(R.string.pollen_allergens))
        Column {
            CheckRow(stringResource(R.string.pollen_grasses), s.grasses) { v -> update { it.copy(grasses = v) } }
            CheckRow(stringResource(R.string.pollen_trees), s.trees) { v -> update { it.copy(trees = v) } }
            CheckRow(stringResource(R.string.pollen_weeds), s.weeds) { v -> update { it.copy(weeds = v) } }
        }
        GroupTitle(stringResource(R.string.pollen_sensitivity))
        ChoiceRow(
            listOf(
                stringResource(R.string.sensitivity_low),
                stringResource(R.string.sensitivity_medium),
                stringResource(R.string.sensitivity_high)
            ),
            s.pollenSensitivity
        ) { v -> update { it.copy(pollenSensitivity = v) } }
    }
}

@Composable
private fun FrostCard(s: AlertState, bundle: WeatherBundle?, fahrenheit: Boolean, update: Updater) {
    val hours = nightHours(bundle)
    AlertCard(
        icon = Icons.Rounded.AcUnit,
        title = stringResource(R.string.frost_title),
        summary = if (s.frostOn) stringResource(R.string.frost_summary_on, formatMinutes(s.frostTime)) else stringResource(R.string.frost_summary_off),
        enabled = s.frostOn,
        onToggle = { v -> update { it.copy(frostOn = v) } }
    ) {
        TimeRow(stringResource(R.string.alerts_alert_time), s.frostTime) { v -> update { it.copy(frostTime = v) } }
        GroupTitle(stringResource(R.string.alerts_active_months))
        MonthChips(s.frostMonths) { v -> update { it.copy(frostMonths = v) } }
        GroupTitle(stringResource(R.string.frost_graph))
        LineChart(
            values = hours.map { (if (fahrenheit) it.temp * 9.0 / 5.0 + 32.0 else it.temp).toFloat() },
            xLabels = axisLabels(hours),
            thresholdValue = if (fahrenheit) 32f else 0f,
            thresholdLabel = stringResource(R.string.frost_line)
        )
    }
}

@Composable
private fun BriefingCard(s: AlertState, update: Updater) {
    AlertCard(
        icon = Icons.Rounded.Bedtime,
        title = stringResource(R.string.briefing_title),
        summary = if (s.briefingOn) stringResource(R.string.briefing_summary_on, formatMinutes(s.briefingTime)) else stringResource(R.string.briefing_summary_off),
        enabled = s.briefingOn,
        onToggle = { v -> update { it.copy(briefingOn = v) } }
    ) {
        Text(
            stringResource(R.string.briefing_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TimeRow(stringResource(R.string.alerts_alert_time), s.briefingTime) { v -> update { it.copy(briefingTime = v) } }
        GroupTitle(stringResource(R.string.briefing_style))
        ChoiceRow(
            listOf(stringResource(R.string.briefing_short), stringResource(R.string.briefing_detailed)),
            if (s.briefingDetailed) 1 else 0
        ) { v -> update { it.copy(briefingDetailed = v == 1) } }
    }
}

@Composable
private fun PressureCard(s: AlertState, bundle: WeatherBundle?, update: Updater) {
    val hours = bundle?.hours?.take(24).orEmpty()
    AlertCard(
        icon = Icons.Rounded.Speed,
        title = stringResource(R.string.pressure_title),
        summary = if (s.pressureOn) stringResource(R.string.pressure_summary_on, s.pressureDrop, formatMinutes(s.pressureTime)) else stringResource(R.string.pressure_summary_off),
        enabled = s.pressureOn,
        onToggle = { v -> update { it.copy(pressureOn = v) } }
    ) {
        TimeRow(stringResource(R.string.alerts_alert_time), s.pressureTime) { v -> update { it.copy(pressureTime = v) } }
        GroupTitle(stringResource(R.string.pressure_graph))
        LineChart(values = hours.map { it.pressure.toFloat() }, xLabels = axisLabels(hours))
        StepSlider(
            label = stringResource(R.string.pressure_drop),
            value = s.pressureDrop,
            range = 1..10,
            valueText = { stringResource(R.string.pressure_drop_value, it) }
        ) { v -> update { it.copy(pressureDrop = v) } }
        Text(
            stringResource(R.string.pressure_drop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val AqiBounds = intArrayOf(0, 20, 40, 60, 80, 100)

@Composable
private fun aqiName(level: Int): String = stringResource(
    when (level) {
        1 -> R.string.aqi_good
        2 -> R.string.aqi_fair
        3 -> R.string.aqi_moderate
        4 -> R.string.aqi_poor
        5 -> R.string.aqi_very_poor
        else -> R.string.aqi_extremely_poor
    }
)

@Composable
private fun AqiCard(s: AlertState, bundle: WeatherBundle?, update: Updater) {
    val hours = todayHours(bundle).filter { it.aqi != null }
    AlertCard(
        icon = Icons.Rounded.Air,
        title = stringResource(R.string.aqi_title),
        summary = if (s.aqiOn) stringResource(R.string.aqi_summary_on, aqiName(s.aqiThreshold), formatMinutes(s.aqiTime)) else stringResource(R.string.aqi_summary_off),
        enabled = s.aqiOn,
        onToggle = { v -> update { it.copy(aqiOn = v) } }
    ) {
        TimeRow(stringResource(R.string.alerts_alert_time), s.aqiTime) { v -> update { it.copy(aqiTime = v) } }
        GroupTitle(stringResource(R.string.aqi_graph))
        LineChart(
            values = hours.map { (it.aqi ?: 0.0).toFloat() },
            xLabels = axisLabels(hours),
            thresholdValue = AqiBounds[s.aqiThreshold - 1].toFloat(),
            thresholdLabel = aqiName(s.aqiThreshold)
        )
        StepSlider(
            label = stringResource(R.string.aqi_threshold),
            value = s.aqiThreshold,
            range = 1..6,
            valueText = { stringResource(R.string.aqi_threshold_value, aqiName(it), AqiBounds[it - 1]) }
        ) { v -> update { it.copy(aqiThreshold = v) } }
        Text(
            stringResource(R.string.aqi_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfficialCard(s: AlertState, update: Updater) {
    val active = listOf(s.yellowOn, s.orangeOn, s.redOn).count { it }
    AlertCard(
        icon = Icons.Rounded.Warning,
        title = stringResource(R.string.official_title),
        summary = if (s.officialOn) stringResource(R.string.official_summary_on, active) else stringResource(R.string.official_summary_off),
        enabled = s.officialOn,
        onToggle = { v -> update { it.copy(officialOn = v) } }
    ) {
        LevelRow(
            Color(0xFFF9C80E),
            stringResource(R.string.official_yellow),
            stringResource(R.string.official_yellow_desc),
            stringResource(R.string.official_yellow_detail),
            s.yellowOn,
            s.officialOn
        ) { v -> update { it.copy(yellowOn = v) } }
        LevelRow(
            Color(0xFFFB8C00),
            stringResource(R.string.official_orange),
            stringResource(R.string.official_orange_desc),
            stringResource(R.string.official_orange_detail),
            s.orangeOn,
            s.officialOn
        ) { v -> update { it.copy(orangeOn = v) } }
        LevelRow(
            Color(0xFFE53935),
            stringResource(R.string.official_red),
            stringResource(R.string.official_red_desc),
            stringResource(R.string.official_red_detail),
            s.redOn,
            s.officialOn
        ) { v -> update { it.copy(redOn = v) } }
    }
}

@Composable
private fun LevelRow(
    color: Color,
    title: String,
    description: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val container by animateColorAsState(
        if (checked && enabled) color.copy(alpha = 0.2f) else scheme.surfaceContainerHighest,
        label = "level-container"
    )
    Surface(shape = RoundedCornerShape(24.dp), color = container) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { open = !open }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
            }
            AnimatedVisibility(open) {
                Text(
                    detail,
                    Modifier.padding(start = 44.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}
