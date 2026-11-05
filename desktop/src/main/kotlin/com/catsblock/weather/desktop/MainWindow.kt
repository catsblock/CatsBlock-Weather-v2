package com.catsblock.weather.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var metric by remember { mutableStateOf<Metric?>(null) }
    var legal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab) {
        metric = null
        legal = null
    }
    AppTheme(settings, conditions?.kind, conditions?.isDay ?: true) {
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.background.luminance() < 0.5f
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            Row(Modifier.fillMaxSize()) {
                NavRail(tab, onTab)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (tab) {
                        2 -> Box(Modifier.fillMaxSize().padding(20.dp)) { MapPage(settings, dark) }
                        1 -> ScrollPage {
                            when (legal) {
                                "privacy" -> PrivacyPage { legal = null }
                                "terms" -> TermsPage { legal = null }
                                "about" -> AboutPage { legal = null }
                                else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(t("Settings"), style = MaterialTheme.typography.headlineMedium)
                                    SettingsPanel(settings, update, onSettings) { legal = it }
                                }
                            }
                        }
                        else -> WeatherTab(settings, conditions, failed, metric, { metric = it }, onTab, onSettings, onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.align(Alignment.TopCenter).widthIn(max = 920.dp).padding(24.dp)) { content() }
    }
}

@Composable
private fun NavRail(tab: Int, onTab: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(scheme.surfaceContainerLow).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(Triple(0, Glyph.Sun, t("Weather")), Triple(2, Glyph.Map, t("Weather map")), Triple(1, Glyph.Settings, t("Settings"))).forEach { (index, glyph, label) ->
            val on = tab == index
            Column(
                Modifier.width(80.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) scheme.primaryContainer else Color.Transparent)
                    .clickable { onTab(index) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GlyphIcon(glyph, if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant, 24.dp)
                Text(
                    label, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeatherTab(
    settings: DesktopSettings,
    c: Conditions?,
    failed: Boolean,
    metric: Metric?,
    onMetric: (Metric?) -> Unit,
    onTab: (Int) -> Unit,
    onSettings: (DesktopSettings) -> Unit,
    onRetry: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    if (!settings.hasCity || c == null) {
        ScrollPage {
            if (!settings.hasCity) {
                Card(Modifier.widthIn(max = 520.dp)) {
                    Text(t("Choose a city to begin"), style = MaterialTheme.typography.titleLarge)
                    CitySearch(settings.language) { onSettings(settings.withCity(it)) }
                }
            } else if (failed) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Weather unavailable"), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onRetry) { Text(t("Retry")) }
                }
            } else Text(t("Loading…"), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    if (metric != null) {
        ScrollPage { MetricDetailPage(metric, settings, c) { onMetric(null) } }
        return
    }
    var day by remember(settings.selected) { mutableStateOf<Day?>(null) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(560.dp)) {
            WeatherBackdrop(c.kind, c.isDay, c.now.toLocalTime(), settings.showBackground, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to scheme.background)
                )
            )
        }
        ScrollPage {
            BoxWithConstraints {
                val wide = maxWidth >= 720.dp
                val tiles = buildList<@Composable (Modifier) -> Unit> {
                    add { WindCard(settings, c, { onMetric(Metric.Wind) }, it) }
                    add { AirCard(c, { onMetric(Metric.Air) }, it) }
                    add { UvCard(c, { onMetric(Metric.Uv) }, it) }
                    add { HumidityCard(settings, c, { onMetric(Metric.Humidity) }, it) }
                    add { PressureCard(settings, c, { onMetric(Metric.Pressure) }, it) }
                    add { VisibilityCard(c, { onMetric(Metric.Visibility) }, it) }
                    add { CloudCard(c, { onMetric(Metric.Cloud) }, it) }
                    add { SunCard(c, it) }
                    add { MoonCard(it) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CityBar(settings, onTab, onSettings, onRetry)
                    HeroCard(settings, c, Modifier.fillMaxWidth())
                    if (settings.showPrecipCard) AlertCard(c, { onMetric(Metric.Precip) }, Modifier.fillMaxWidth())
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
    }
}

@Composable
private fun CityBar(settings: DesktopSettings, onTab: (Int) -> Unit, onSettings: (DesktopSettings) -> Unit, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.cities.forEachIndexed { i, city ->
                val on = i == settings.selected
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (on) 0.34f else 0.16f))
                        .clickable { onSettings(settings.copy(selected = i)) }.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(city.name, color = Color.White, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onRetry).padding(10.dp)) {
            GlyphIcon(Glyph.Refresh, Color.White)
        }
        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).clickable { onTab(1) }.padding(10.dp)) {
            GlyphIcon(Glyph.Plus, Color.White)
        }
    }
}
