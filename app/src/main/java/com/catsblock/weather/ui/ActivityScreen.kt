package com.catsblock.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.data.ActivityStats
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.TemperatureRecord
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

private const val SOURCE_CODE_URL = "https://github.com/catsblock/CatsBlock-Weather-v2"

private data class DataSource(val name: String, val purpose: String)

private val dataSources = listOf(
    DataSource("Open-Meteo", "Forecasts, air quality and city search. This is the default provider."),
    DataSource("WeatherAPI.com, Visual Crossing, OpenWeather", "Optional forecast providers. They are used only if you select them."),
    DataSource("OSRM routing service", "Driving routes for the travel planner."),
    DataSource("RainViewer", "Rain radar images."),
    DataSource("NASA FIRMS, EONET, GIBS and SVS", "Fire hotspots, natural events, satellite imagery and moon images."),
    DataSource("OpenStreetMap and CARTO", "Map tiles."),
    DataSource("ArcGIS CAP alerts feed", "Public weather alerts published by government agencies."),
    DataSource("GitHub", "App update checks, weather icons and background images."),
    DataSource("Google Sound Library", "Ambient weather sounds.")
)

private val safetyParagraphs = listOf(
    "Why can Play Protect show a warning?" to
        "CatsBlock Weather is distributed outside of Google Play. Google Play Protect may show an unrecognized app notice for apps installed from outside Google Play that Google has not reviewed. Such a notice means the app has not been reviewed by Google. It is not a finding that the app contains malware.",
    "Install permission" to
        "The app declares the permission to install packages. It is used only by the built-in updater: when you choose Update, the new version is downloaded from the project's GitHub releases and handed to Android's installer, which always asks for your confirmation. Apps with this capability can be evaluated more strictly.",
    "How permissions are used" to
        "Location is used only to show the weather where you are. The camera is used only by the live moon view. Notifications are used only for the alerts you enable. Background work is used only to refresh the weather and download updates.",
    "What the app does not include" to
        "The app has no advertising or analytics libraries and does not require an account. Your settings and activity statistics are stored only on this device. Coordinates of the selected city are sent to the weather provider you choose so it can return a forecast.",
    "Open source" to
        "The source code is public so anyone can review it. Install the app only from the official project releases, and only if you trust the download source.",
    "Notice" to
        "This information is provided for transparency. Google Play Protect is operated by Google and its decisions are made independently. The app is provided as is, without warranty of any kind. Weather data can be inaccurate and must not be your only source for safety-critical decisions."
)

@Composable
fun ActivityDialog(stats: ActivityStats, fahrenheit: Boolean, onClose: () -> Unit) {
    var sourcesOpen by remember { mutableStateOf(false) }
    if (sourcesOpen) {
        SourcesDialog { sourcesOpen = false }
    }
    FullScreenDialog(tr("Activity"), onClose) {
        val today = LocalDate.now()
        val installed = stats.installedAtMillis.takeIf { it > 0L }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

        HeroTile(
            icon = Icons.Rounded.CalendarMonth,
            label = tr("Installed on"),
            value = installed?.let { dateText(it) } ?: tr("Unknown"),
            sub = installed?.let { agoText(ChronoUnit.DAYS.between(it, today).toInt()) }
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                Icons.Rounded.EventAvailable,
                tr("Check-in days"),
                stats.checkInDays.toString(),
                tr("Days you opened the app"),
                Modifier.weight(1f)
            )
            StatTile(
                Icons.Rounded.Insights,
                tr("Observed conditions"),
                stats.observed.size.toString(),
                tr("Types of weather seen"),
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                Icons.Rounded.AcUnit,
                tr("Coldest"),
                stats.coldest?.let { tempText(it.celsius, fahrenheit) } ?: "—",
                stats.coldest?.let { recordSub(it) } ?: tr("No data yet"),
                Modifier.weight(1f),
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
            StatTile(
                Icons.Rounded.WbSunny,
                tr("Hottest"),
                stats.hottest?.let { tempText(it.celsius, fahrenheit) } ?: "—",
                stats.hottest?.let { recordSub(it) } ?: tr("No data yet"),
                Modifier.weight(1f),
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        SectionTitle(tr("Weather in your region"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountTile(Icons.Rounded.WaterDrop, tr("Rainy days"), stats.rainyDays, Modifier.weight(1f))
            CountTile(Icons.Rounded.AcUnit, tr("Snowy days"), stats.snowyDays, Modifier.weight(1f))
            CountTile(Icons.Rounded.Grain, tr("Hail days"), stats.hailDays, Modifier.weight(1f))
        }

        SectionTitle(tr("Observed conditions"))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                if (stats.observed.isEmpty()) {
                    Text(
                        tr("No data yet"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val rows = stats.observed.entries.sortedByDescending { it.value }
                    rows.forEachIndexed { index, (kind, days) ->
                        if (index > 0) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        ConditionRow(kind, days)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            tr("All statistics are stored only on this device."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { sourcesOpen = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("Data sources and safety"))
        }
    }
}

@Composable
private fun SourcesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    FullScreenDialog(tr("Data sources and safety"), onClose) {
        SectionTitle(tr("Where the data comes from"), top = 0.dp)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                dataSources.forEachIndexed { index, source ->
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(source.name, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tr(source.purpose),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SectionTitle(tr("Is this app safe?"))
        safetyParagraphs.forEach { (title, body) ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_CODE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors()
        ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("View source code"))
        }
    }
}

@Composable
private fun FullScreenDialog(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroTile(icon: ImageVector, label: String, value: String, sub: String?) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(shape = RoundedCornerShape(28.dp), color = container, modifier = modifier) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = content)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.7f), maxLines = 2)
        }
    }
}

@Composable
private fun CountTile(icon: ImageVector, label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ConditionRow(kind: Condition, days: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(conditionIcon(kind), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(kind.label(true), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(daysText(days), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String, top: androidx.compose.ui.unit.Dp = 24.dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 8.dp, top = top, bottom = 10.dp)
    )
}

private fun conditionIcon(kind: Condition): ImageVector = when (kind) {
    Condition.Storm -> Icons.Rounded.Thunderstorm
    Condition.Rain, Condition.Drizzle -> Icons.Rounded.WaterDrop
    Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Sleet -> Icons.Rounded.AcUnit
    Condition.Hail -> Icons.Rounded.Grain
    Condition.Fog -> Icons.Rounded.Grain
    Condition.Clear, Condition.FewClouds -> Icons.Rounded.WbSunny
    else -> Icons.Rounded.Insights
}

private fun dateText(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(I18n.lang.locale))

private fun daysText(days: Int): String =
    if (days == 1) tr("1 day") else String.format(I18n.lang.locale, tr("%1\$d days"), days)

private fun agoText(days: Int): String = when (days) {
    0 -> tr("Today")
    1 -> tr("Yesterday")
    else -> String.format(I18n.lang.locale, tr("%1\$d days ago"), days)
}

private fun recordSub(record: TemperatureRecord): String =
    "${record.place} · ${dateText(record.date)}"
