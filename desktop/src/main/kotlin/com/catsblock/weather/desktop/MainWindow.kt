package com.catsblock.weather.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun MainWindow(
    settings: DesktopSettings,
    conditions: Conditions?,
    failed: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    onSettings: (DesktopSettings) -> Unit,
    onRetry: () -> Unit
) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onTab(0) }) {
                        Text(t("Weather"), fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal)
                    }
                    TextButton(onClick = { onTab(1) }) {
                        Text(t("Settings"), fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                HorizontalDivider()
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    if (tab == 0) WeatherPanel(settings, conditions, failed, onRetry) else SettingsPanel(settings, onSettings)
                }
            }
        }
    }
}

@Composable
private fun WeatherPanel(settings: DesktopSettings, c: Conditions?, failed: Boolean, onRetry: () -> Unit) {
    if (!settings.hasCity) {
        Text(t("Choose a city to begin"), style = MaterialTheme.typography.titleMedium)
        return
    }
    if (c == null) {
        if (failed) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Weather unavailable"), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRetry) { Text(t("Retry")) }
            }
        } else {
            Text(t("Loading…"), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(settings.city + if (settings.country.isNotBlank()) ", " + settings.country else "", style = MaterialTheme.typography.titleMedium)
            Text(formatTemp(c.tempC, settings.fahrenheit), fontSize = 72.sp, fontWeight = FontWeight.Light)
            Text(t(conditionKey(c.code)), style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Metric(t("Feels like"), formatTemp(c.feelsC, settings.fahrenheit))
            Metric(t("Humidity"), c.humidity.toString() + "%")
            Metric(t("Wind"), (if (settings.fahrenheit) (c.windKmh * 0.621371).roundToInt().toString() + " mph" else c.windKmh.roundToInt().toString() + " km/h"))
        }
        HorizontalDivider()
        Text(t("Hourly"), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items(c.hourly) { h ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(h.label, style = MaterialTheme.typography.labelMedium)
                    Text(formatTemp(h.tempC, settings.fahrenheit), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        HorizontalDivider()
        Text(t("Daily"), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            c.daily.forEach { d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.label, Modifier.width(64.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(t(conditionKey(d.code)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatTemp(d.maxC, settings.fahrenheit) + " / " + formatTemp(d.minC, settings.fahrenheit),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SettingsPanel(settings: DesktopSettings, onChange: (DesktopSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Section(t("City")) {
            if (settings.hasCity) Text(settings.city + ", " + settings.country, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.trim().length >= 2) {
                        scope.launch {
                            results = withContext(Dispatchers.IO) { WeatherClient.search(it, settings.language) }
                        }
                    } else results = null
                },
                label = { Text(t("Search city")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            results?.let { list ->
                if (list.isEmpty()) Text(t("No results"), style = MaterialTheme.typography.bodyMedium)
                list.forEach { p ->
                    Text(
                        p.name + ", " + p.country,
                        Modifier.fillMaxWidth().clickable {
                            onChange(settings.copy(city = p.name, country = p.country, lat = p.lat, lon = p.lon))
                            query = ""; results = null
                        }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    HorizontalDivider()
                }
            }
        }
        Section(t("Temperature unit")) {
            Choice(!settings.fahrenheit, t("Celsius")) { onChange(settings.copy(fahrenheit = false)) }
            Choice(settings.fahrenheit, t("Fahrenheit")) { onChange(settings.copy(fahrenheit = true)) }
        }
        Section(t("Widget")) {
            Toggle(t("Show floating widget"), settings.widgetVisible) { onChange(settings.copy(widgetVisible = it)) }
            Toggle(t("Always on top"), settings.alwaysOnTop) { onChange(settings.copy(alwaysOnTop = it)) }
            Text(t("Widget color"), style = MaterialTheme.typography.labelLarge)
            Choice(settings.colorMode == "weather", t("Follow the weather")) { onChange(settings.copy(colorMode = "weather")) }
            Choice(settings.colorMode == "fixed", t("Fixed color")) { onChange(settings.copy(colorMode = "fixed")) }
            if (settings.colorMode == "fixed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FIXED_COLORS.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(color, RoundedCornerShape(4.dp))
                                .clickable { onChange(settings.copy(fixedColor = index)) }
                        ) {
                            if (index == settings.fixedColor) {
                                Box(Modifier.align(Alignment.Center).size(8.dp).background(inkFor(color), RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }
            Text(t("Inactive opacity") + ": " + settings.inactivePercent + "%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = settings.inactivePercent.toFloat(),
                onValueChange = { onChange(settings.copy(inactivePercent = it.roundToInt())) },
                valueRange = 10f..60f
            )
            Text(t("Return to inactive after"), style = MaterialTheme.typography.labelLarge)
            listOf(0, 3, 5, 10, 30).forEach { seconds ->
                Choice(settings.idleSeconds == seconds, if (seconds == 0) t("Never") else "$seconds s") {
                    onChange(settings.copy(idleSeconds = seconds))
                }
            }
        }
        Section(t("Language")) {
            LANGUAGES.forEach { (code, label) ->
                Choice(settings.language == code, label) { onChange(settings.copy(language = code)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
