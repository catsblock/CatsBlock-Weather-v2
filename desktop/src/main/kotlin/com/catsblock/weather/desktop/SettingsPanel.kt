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
fun SettingsPanel(settings: DesktopSettings, update: UpdateUi, onChange: (DesktopSettings) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Section(t("Saved cities")) {
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
        Section(t("Units")) {
            Text(t("Temperature unit"), style = MaterialTheme.typography.labelLarge)
            Segmented(listOf(false to t("Celsius"), true to t("Fahrenheit")), settings.fahrenheit) { onChange(settings.copy(fahrenheit = it)) }
            Text(t("Wind unit"), style = MaterialTheme.typography.labelLarge)
            Segmented(WindUnit.entries.map { it to it.label }, settings.windUnit) { onChange(settings.copy(windUnit = it)) }
            Text(t("Pressure unit"), style = MaterialTheme.typography.labelLarge)
            Segmented(PressureUnit.entries.map { it to it.label }, settings.pressureUnit) { onChange(settings.copy(pressureUnit = it)) }
        }
        Section(t("Appearance")) {
            Text(t("Theme"), style = MaterialTheme.typography.labelLarge)
            Segmented(
                listOf(ThemeMode.System to t("System"), ThemeMode.Light to t("Light theme"), ThemeMode.Dark to t("Dark")),
                settings.theme
            ) { onChange(settings.copy(theme = it)) }
        }
        Section(t("Widget")) {
            Toggle(t("Show floating widget"), settings.widgetVisible) { onChange(settings.copy(widgetVisible = it)) }
            Toggle(t("Always on top"), settings.alwaysOnTop) { onChange(settings.copy(alwaysOnTop = it)) }
            Text(t("Widget color"), style = MaterialTheme.typography.labelLarge)
            Segmented(listOf("weather" to t("Follow the weather"), "fixed" to t("Fixed color")), settings.colorMode) { onChange(settings.copy(colorMode = it)) }
            if (settings.colorMode == "fixed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FIXED_COLORS.forEachIndexed { index, color ->
                        Box(Modifier.size(32.dp).background(color, CardShape).clickable { onChange(settings.copy(fixedColor = index)) }) {
                            if (index == settings.fixedColor) Box(Modifier.align(Alignment.Center).size(8.dp).background(inkFor(color), CardShape))
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
            Segmented(
                listOf(0, 3, 5, 10, 30).map { it to if (it == 0) t("Never") else "$it s" },
                settings.idleSeconds
            ) { onChange(settings.copy(idleSeconds = it)) }
        }
        Section(t("Language")) {
            LANGUAGES.forEach { (code, label) -> Choice(settings.language == code, label) { onChange(settings.copy(language = code)) } }
        }
        Section(t("Running in the background")) {
            Toggle(t("Start with Windows"), settings.startWithWindows) { onChange(settings.copy(startWithWindows = it)) }
        }
        Section(updateText("Check for updates")) {
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
