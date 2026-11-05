package com.catsblock.weather.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class UpdateUi(val pending: WindowsRelease?, val status: UpdateStatus, val onCheck: () -> Unit, val onInstall: () -> Unit)

@Composable
fun CitySearch(language: String, onPick: (Place) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(300)
        results = withContext(Dispatchers.IO) { WeatherClient.search(query, language) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            label = { Text(t("Search city")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        results?.let { list ->
            if (list.isEmpty()) Text(t("No results"), style = MaterialTheme.typography.bodyMedium)
            list.forEach { p ->
                Text(
                    if (p.country.isBlank()) p.name else p.name + ", " + p.country,
                    Modifier.fillMaxWidth().clickable { onPick(p); query = ""; results = null }.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

fun DesktopSettings.withCity(p: Place): DesktopSettings {
    val city = SavedCity(p.name, p.country, p.lat, p.lon)
    val existing = cities.indexOfFirst { it.id == city.id }
    return if (existing >= 0) copy(selected = existing) else copy(cities = cities + city, selected = cities.size)
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        GlyphIcon(Glyph.Chevron, scheme.onSurfaceVariant, 18.dp)
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SettingsPanel(settings: DesktopSettings, update: UpdateUi, onChange: (DesktopSettings) -> Unit, onLegal: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), t("Saved cities")) {
            settings.cities.forEachIndexed { i, city ->
                Row(Modifier.fillMaxWidth().clickable { onChange(settings.copy(selected = i)) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        city.title, Modifier.weight(1f).padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (i == settings.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        Modifier.clickable {
                            val next = settings.cities.filterIndexed { index, _ -> index != i }
                            onChange(settings.copy(cities = next, selected = settings.selected.coerceAtMost(maxOf(0, next.lastIndex))))
                        }.padding(8.dp)
                    ) { GlyphIcon(Glyph.Close, MaterialTheme.colorScheme.onSurfaceVariant, 16.dp) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            CitySearch(settings.language) { onChange(settings.withCity(it)) }
        }
        Card(Modifier.fillMaxWidth(), t("Appearance")) {
            Label(t("Theme"))
            Segmented(
                listOf(ThemeMode.System to t("System"), ThemeMode.Light to t("Light theme"), ThemeMode.Dark to t("Dark")),
                settings.theme
            ) { onChange(settings.copy(theme = it)) }
            Toggle(t("AMOLED black"), settings.amoled) { onChange(settings.copy(amoled = it)) }
            Hint(t("Use a pure black background when the dark theme is active."))
            Label(t("Color"))
            Segmented(listOf("weather" to t("Weather"), "custom" to t("Custom")), settings.colorStyle) { onChange(settings.copy(colorStyle = it)) }
            if (settings.colorStyle == "weather") Hint(t("Colors follow the current weather. This is the default."))
            else {
                Hint(t("Pick your own accent color."))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FIXED_COLORS.forEachIndexed { index, color ->
                        Box(Modifier.size(36.dp).background(color, SmallShape).clickable { onChange(settings.copy(customColor = index)) }) {
                            if (index == settings.customColor) Box(Modifier.align(Alignment.Center).size(10.dp).background(inkFor(color), SmallShape))
                        }
                    }
                }
            }
            Label(t("Text size") + ": " + settings.textScale + "%")
            Slider(
                value = settings.textScale.toFloat(),
                onValueChange = { onChange(settings.copy(textScale = (it / 5f).roundToInt() * 5)) },
                valueRange = 85f..130f
            )
            TextButton(onClick = { onChange(settings.copy(textScale = 100)) }) { Text(t("Reset")) }
        }
        Card(Modifier.fillMaxWidth(), t("Layout")) {
            Toggle(t("Compact current conditions"), settings.compactHero) { onChange(settings.copy(compactHero = it)) }
            Hint(t("Use a shorter header so more cards fit on the screen."))
            Toggle(t("Precipitation card"), settings.showPrecipCard) { onChange(settings.copy(showPrecipCard = it)) }
            Toggle(t("Weather background"), settings.showBackground) { onChange(settings.copy(showBackground = it)) }
        }
        Card(Modifier.fillMaxWidth(), t("Region and units")) {
            Label(t("Temperature"))
            Segmented(listOf(false to t("Celsius"), true to t("Fahrenheit")), settings.fahrenheit) { onChange(settings.copy(fahrenheit = it)) }
            Label(t("Wind speed"))
            Segmented(WindUnit.entries.map { it to it.label }, settings.windUnit) { onChange(settings.copy(windUnit = it)) }
            Label(t("Atmospheric pressure"))
            Segmented(PressureUnit.entries.map { it to it.label }, settings.pressureUnit) { onChange(settings.copy(pressureUnit = it)) }
            Label(t("Precipitation"))
            Segmented(PrecipUnit.entries.map { it to it.label }, settings.precipUnit) { onChange(settings.copy(precipUnit = it)) }
        }
        Card(Modifier.fillMaxWidth(), t("Weather data")) {
            Label(t("Update interval"))
            Segmented(listOf(5, 15, 30, 60).map { it to "$it min" }, settings.refreshMinutes) { onChange(settings.copy(refreshMinutes = it)) }
        }
        Card(Modifier.fillMaxWidth(), t("Widget")) {
            Toggle(t("Show floating widget"), settings.widgetVisible) { onChange(settings.copy(widgetVisible = it)) }
            Toggle(t("Always on top"), settings.alwaysOnTop) { onChange(settings.copy(alwaysOnTop = it)) }
            Label(t("Widget size") + ": " + settings.widgetScale + "%")
            Slider(
                value = settings.widgetScale.toFloat(),
                onValueChange = { onChange(settings.copy(widgetScale = (it / 10f).roundToInt() * 10)) },
                valueRange = 50f..250f
            )
            TextButton(onClick = { onChange(settings.copy(widgetScale = 100)) }) { Text(t("Reset")) }
            Label(t("Widget color"))
            Segmented(listOf("weather" to t("Follow the weather"), "fixed" to t("Fixed color")), settings.colorMode) { onChange(settings.copy(colorMode = it)) }
            if (settings.colorMode == "fixed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FIXED_COLORS.forEachIndexed { index, color ->
                        Box(Modifier.size(36.dp).background(color, SmallShape).clickable { onChange(settings.copy(fixedColor = index)) }) {
                            if (index == settings.fixedColor) Box(Modifier.align(Alignment.Center).size(10.dp).background(inkFor(color), SmallShape))
                        }
                    }
                }
            }
            Label(t("Inactive opacity") + ": " + settings.inactivePercent + "%")
            Slider(
                value = settings.inactivePercent.toFloat(),
                onValueChange = { onChange(settings.copy(inactivePercent = it.roundToInt())) },
                valueRange = 10f..60f
            )
            Label(t("Return to inactive after"))
            Segmented(
                listOf(0, 3, 5, 10, 30).map { it to if (it == 0) t("Never") else "$it s" },
                settings.idleSeconds
            ) { onChange(settings.copy(idleSeconds = it)) }
        }
        Card(Modifier.fillMaxWidth(), t("App language")) {
            LANGUAGES.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { (code, label) -> Box(Modifier.weight(1f)) { Choice(settings.language == code, label) { onChange(settings.copy(language = code)) } } }
                    if (pair.size == 1) Box(Modifier.weight(1f)) {}
                }
            }
        }
        Card(Modifier.fillMaxWidth(), t("Running in the background")) {
            Toggle(t("Start with Windows"), settings.startWithWindows) { onChange(settings.copy(startWithWindows = it)) }
        }
        Card(Modifier.fillMaxWidth(), t("About & Legal")) {
            NavRow(t("About this app"), null) { onLegal("about") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(t("Privacy Policy"), t("How CatsBlock Weather handles your data.")) { onLegal("privacy") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(t("Terms of Service"), t("The rules for using CatsBlock Weather and the Agent.")) { onLegal("terms") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(t("App version") + " 2.0." + WindowsUpdater.installedBuild(), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val release = update.pending
            if (release != null) {
                TextButton(onClick = update.onInstall, enabled = update.status != UpdateStatus.Downloading) {
                    Text(if (update.status == UpdateStatus.Downloading) updateText("Downloading update…") else updateText("Install update") + " (" + release.name + ")")
                }
            } else {
                TextButton(onClick = update.onCheck, enabled = update.status != UpdateStatus.Checking) {
                    Text(
                        when (update.status) {
                            UpdateStatus.Checking -> updateText("Checking for updates…")
                            UpdateStatus.Current -> updateText("Up to date")
                            UpdateStatus.Failed -> updateText("Update failed. Try again")
                            else -> updateText("Check for updates")
                        }
                    )
                }
            }
        }
    }
}
