package com.catsblock.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.catsblock.weather.i18n.tr

private val licenseSections = listOf(
    "Application" to
        "CatsBlock Weather is licensed under the GNU General Public License, version 3 or (at your option) any later version. Copyright (C) 2026 Isaac (CatsBlock Corporation). The complete license text is retained in the project LICENSE file.",
    "Bundled artwork" to
        "The bundled Twemoji artwork is from jdecked/twemoji. Copyright 2020 Twitter, Inc. and other contributors. The artwork is licensed under Creative Commons Attribution 4.0 International (CC BY 4.0). The included third_party/twemoji/NOTICE.md records the artwork, conversion and attribution details; the full license is retained in third_party/twemoji/LICENSE.",
    "Map attribution" to
        "Map tiles display © OpenStreetMap contributors. The app also displays RainViewer attribution when its precipitation map overlay is active. Map data and services are provided by their respective providers and are subject to their own terms and notices.",
    "Weather data attribution" to
        "Weather data integrations present in the app include Open-Meteo, WeatherAPI.com, Visual Crossing and OpenWeather. Which source is used depends on the selected source and configuration. Each provider's data, trademarks and service are subject to that provider's own license, terms and attribution requirements; this screen does not grant rights to provider content.",
    "Dependencies, fonts and interface icons" to
        "The Android app uses Jetpack Compose and the Compose Material Icons Extended dependency declared in app/build.gradle.kts. No custom font files are bundled in the Android resources; typography uses the app's configured font choices and Android font fallback. Dependency and provider rights remain governed by their respective distributed license texts and terms."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(vm: WeatherViewModel) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("License")) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(20.dp)) {
                    licenseSections.forEachIndexed { index, (title, body) ->
                        if (index > 0) Spacer(Modifier.height(18.dp))
                        Text(tr(title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(body),
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
