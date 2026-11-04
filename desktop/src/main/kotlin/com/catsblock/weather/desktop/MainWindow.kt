package com.catsblock.weather.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box

@Composable
fun MainWindow(
    settings: DesktopSettings,
    conditions: Conditions?,
    failed: Boolean,
    tab: Int,
    update: UpdateUi,
    onTab: (Int) -> Unit,
    onSettings: (DesktopSettings) -> Unit,
    onRetry: () -> Unit
) {
    AppTheme(settings.theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopBar(settings, tab, onTab, onSettings, onRetry)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.align(Alignment.TopCenter).widthIn(max = 1040.dp).padding(24.dp)) {
                        if (tab == 1) SettingsPanel(settings, update, onSettings)
                        else WeatherPage(settings, conditions, failed, onSettings, onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(settings: DesktopSettings, tab: Int, onTab: (Int) -> Unit, onSettings: (DesktopSettings) -> Unit, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (tab == 1) {
            Box(Modifier.clickable { onTab(0) }.padding(8.dp)) { GlyphIcon(Glyph.Back, scheme.onSurface) }
            Text(t("Settings"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
        } else {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (settings.cities.isEmpty()) Text(t("Weather"), style = MaterialTheme.typography.titleLarge)
                settings.cities.forEachIndexed { i, city ->
                    val on = i == settings.selected
                    Column(Modifier.clickable { onSettings(settings.copy(selected = i)) }.padding(vertical = 8.dp)) {
                        Text(
                            city.name, fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) scheme.onSurface else scheme.onSurfaceVariant
                        )
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().background(if (on) scheme.primary else androidx.compose.ui.graphics.Color.Transparent).padding(vertical = 1.dp))
                    }
                }
            }
            Box(Modifier.clickable(onClick = onRetry).padding(8.dp)) { GlyphIcon(Glyph.Refresh, scheme.onSurface) }
            Box(Modifier.clickable { onTab(1) }.padding(8.dp)) { GlyphIcon(Glyph.Settings, scheme.onSurface) }
        }
    }
}

@Composable
private fun WeatherPage(settings: DesktopSettings, c: Conditions?, failed: Boolean, onSettings: (DesktopSettings) -> Unit, onRetry: () -> Unit) {
    if (!settings.hasCity) {
        Card(Modifier.widthIn(max = 520.dp)) {
            Text(t("Choose a city to begin"), style = MaterialTheme.typography.titleLarge)
            CitySearch(settings.language) { onSettings(settings.withCity(it)) }
        }
        return
    }
    if (c == null) {
        if (failed) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Weather unavailable"), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRetry) { Text(t("Retry")) }
            }
        } else Text(t("Loading…"), style = MaterialTheme.typography.titleMedium)
        return
    }
    var day by remember(settings.selected) { mutableStateOf<Day?>(null) }
    BoxWithConstraints {
        val wide = maxWidth >= 760.dp
        val tiles = listOf<@Composable (Modifier) -> Unit>(
            { WindCard(settings, c, it) }, { AirCard(c, it) }, { UvCard(c, it) }, { HumidityCard(c, it) },
            { SunCard(c, it) }, { PressureCard(settings, c, it) }, { VisibilityCard(c, it) }, { MoonCard(it) }
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroCard(settings, c, Modifier.fillMaxWidth())
            AlertCard(c, Modifier.fillMaxWidth())
            HourlyCard(settings, c, Modifier.fillMaxWidth())
            DailyCard(settings, c, { day = if (day == it) null else it }, Modifier.fillMaxWidth())
            day?.let { DayDetail(settings, c, it) { day = null } }
            tiles.chunked(if (wide) 2 else 1).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { tile -> tile(Modifier.weight(1f)) }
                    if (row.size == 1 && wide) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
