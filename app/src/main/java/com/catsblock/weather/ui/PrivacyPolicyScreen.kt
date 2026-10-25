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

private val policySections = listOf(
    "Company and app" to
        "This Privacy Policy applies to the application CatsBlock Weather (the \"App\"), published by CatsBlock Corporation (\"CatsBlock Corporation\", \"we\", \"us\").",
    "Agreement" to
        "By downloading, opening or using CatsBlock Weather, you acknowledge that you have read this Privacy Policy and agree to it. If you do not agree, do not use the App.",
    "No personal data collected" to
        "CatsBlock Corporation does not collect, store or share any personal data. The App has no accounts, analytics or advertising, and location coordinates are never associated with your identity.",
    "Location permission" to
        "If you allow location access, your position is used only locally on your device, at runtime, to find the weather where you are. It is never stored on servers owned by CatsBlock Corporation.",
    "Data stored on your device" to
        "Saved cities and your preferences are stored only on your device and can be removed at any time by clearing the App data or uninstalling the App.",
    "Third-party public APIs" to
        "To show weather, city coordinates are requested from free, public third-party services such as Open-Meteo. Moon images and map tiles are loaded from public providers such as NASA and OpenStreetMap. These services are not operated by CatsBlock Corporation and are governed by their own terms.",
    "Cloudflare Worker proxy" to
        "When the WeatherAPI.com, Visual Crossing or OpenWeather data source is selected in Settings, the App sends the requested coordinates through a Cloudflare Worker operated by CatsBlock Corporation. This proxy exists only to keep API keys off your device and to limit abuse. It temporarily processes your IP address to enforce a request limit, briefly caches the weather response for the requested coordinates to reduce load on upstream providers, and never stores your precise location history, your identity or any data beyond what is strictly needed for that single request. No personal profile is built from this processing. The Open-Meteo data source does not use this proxy.",
    "Disclaimer of liability" to
        "The App is provided \"as is\" and \"as available\", without warranties of any kind. CatsBlock Corporation expressly disclaims all liability for the state, performance, errors, interruptions or availability of the software, and for any decision or loss resulting from its use. The accuracy, completeness and availability of weather data are the exclusive responsibility of the free public APIs used by the App.",
    "No individual support" to
        "CatsBlock Corporation does not provide individual support and offers no contact channel. Requests, questions or messages cannot be answered."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(vm: WeatherViewModel) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("Privacy Policy")) },
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
                    policySections.forEachIndexed { index, (title, body) ->
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
