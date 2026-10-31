package com.catsblock.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.detail.entrance

private data class InfoSection(val icon: ImageVector, val title: String, val body: String)

private val infoSections = listOf(
    InfoSection(
        Icons.Rounded.AutoAwesome,
        "About CatsBlock Weather",
        "CatsBlock Weather is a weather app focused on clear forecasts, customization and transparency. It shows current conditions, hourly and daily forecasts, rain radar, air quality, sun and moon details, alerts, and a travel planner that checks the weather along a route."
    ),
    InfoSection(
        Icons.Rounded.Settings,
        "How the app works",
        "The app reads your location if you allow it, or uses the city you choose, requests forecasts from public weather services and turns them into cards that open into detailed views. Background updates and alerts run on your device at the interval you set. You can choose the data provider, add your own access keys and limit updates to Wi-Fi. Everything you configure stays on your device."
    ),
    InfoSection(
        Icons.Rounded.Compare,
        "How it differs from other apps",
        "Compared with many common weather apps, CatsBlock Weather is designed to have no advertising, no analytics libraries and no account. It lets you choose between independent data providers, shows where each piece of data comes from, offers deep customization of icons, backgrounds, sounds, fonts, colors and a pure black theme, gives you control over mobile data and battery use, and publishes its source code for review. These statements describe our design goals and are not claims about any specific app, and other apps may offer features this app does not."
    ),
    InfoSection(
        Icons.Rounded.Warning,
        "Why Play Protect may warn during installation",
        "Google Play Protect checks apps when they are installed. When an app comes from outside Google Play and Google has no earlier information about that exact build, Play Protect may show a notice and offer to scan it. This is a precaution for unfamiliar apps and is not a finding of harmful behavior."
    ),
    InfoSection(
        Icons.Rounded.Update,
        "Why the warning stops after scanning again",
        "When you let Play Protect scan the app, Google analyzes it and remembers the result. On later checks, an app that was scanned and showed no harmful behavior is no longer treated as unfamiliar, so the notice normally does not return. Play Protect behavior can vary by device, Android version and Google updates, and CatsBlock Corporation cannot control it."
    ),
    InfoSection(
        Icons.Rounded.Shield,
        "Installing safely",
        "Download the app only from the official project releases. If Play Protect offers to scan the app, you can allow the scan. You can uninstall the app at any time, which also removes the data stored on your device."
    ),
    InfoSection(
        Icons.Rounded.Info,
        "Notice",
        "This page is for information only and does not replace the Privacy Policy. The app is provided as is, without warranty of any kind. Third-party services, including Google Play Protect, operate independently of CatsBlock Corporation."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(vm: WeatherViewModel) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("About this app")) },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) }
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            infoSections.forEachIndexed { index, section ->
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().entrance(index)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(section.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(tr(section.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr(section.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
