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
    "Controller and legal bases" to
        "For the limited technical data described here, CatsBlock Corporation acts as data controller. Where the GDPR, UK GDPR, LGPD or similar laws apply, processing relies on legitimate interests in operating, securing and protecting the service from abuse and, for location, on your consent, which you can withdraw at any time in your device settings.",
    "Data we process" to
        "CatsBlock Corporation does not run user accounts, analytics or advertising, does not sell your data and does not build user profiles. The App stores saved cities and preferences on your device. When a Cloudflare Worker data source is used, limited technical data is processed to operate and protect the service: your IP address and approximate coordinates, as described below.",
    "Location permission" to
        "If you allow location access, your position is read on your device to find the weather where you are. When a data source that uses the Cloudflare Worker is selected, approximate coordinates (rounded to about 1 km) are sent through it. You can revoke location permission at any time in your device settings.",
    "Data stored on your device" to
        "Saved cities and your preferences are stored only on your device and can be removed at any time by clearing the App data or uninstalling the App.",
    "Visual effects and touch input" to
        "Rain, snow and touch effects are drawn on your device using its graphics processor. Where you touch a weather card is used only to animate the effect on screen; touch positions are never stored, logged or sent anywhere.",
    "Third-party public APIs" to
        "To show weather, coordinates and city searches are sent to free, public third-party services such as Open-Meteo, WeatherAPI.com, Visual Crossing and OpenWeather. Moon images and map tiles are loaded from public providers such as NASA and OpenStreetMap. These providers receive your IP address as part of the connection, are not operated by CatsBlock Corporation, and are governed by their own terms and privacy policies.",
    "Travel routes" to
        "When you use the travel planner, the places you type are sent to Open-Meteo to find their coordinates, the start and destination coordinates are sent to the public OSRM routing service to calculate the route, and coordinates of points along the route are sent to Open-Meteo to get the forecast. These services are not operated by CatsBlock Corporation. The planner does not store routes on any CatsBlock Corporation server.",
    "Cloudflare Worker proxy" to
        "When the WeatherAPI.com, Visual Crossing or OpenWeather data source or the Agent is used, requests go through a Cloudflare Worker operated by CatsBlock Corporation, which keeps API keys off your device and limits abuse. To enforce limits, a Durable Object and Cloudflare KV keep counters tied to an irreversible hash of your IP address, never the original, for at most 24 hours. Your country, derived by Cloudflare from the connection, is checked per request and not stored. No device identifier is collected. Weather responses for rounded coordinates are cached for about 15 minutes. Cloudflare may process connection data under its own terms. The Open-Meteo source does not use this proxy.",
    "AI Agent" to
        "The Agent sends your message, the selected city and rounded coordinates through the Worker to Google's Gemini API and public weather services, which process them under their own terms. Do not enter personal, sensitive or confidential information. Replies are AI-generated, may be inaccurate and are not professional weather, safety or emergency advice; no decision with legal or similarly significant effects is made about you. CatsBlock Corporation does not store your messages or replies. The activity panel shows real technical steps, not the AI's reasoning.",
    "International transfers" to
        "Data is processed on Cloudflare's global network and by the third-party providers named in this policy, which may be located outside your country, including outside the EEA and the UK. Where required, transfers rely on the safeguards those providers offer, such as Standard Contractual Clauses.",
    "Your rights" to
        "Depending on where you live (including under the GDPR, UK GDPR, LGPD, CCPA/CPRA and PIPEDA), you may have rights to access, correct, delete, restrict or port personal data, to object to processing and to withdraw consent. CatsBlock Corporation does not sell or share personal information and does not use it for advertising or profiling. Because it offers no accounts or contact channel and keeps only irreversible hashes that cannot be linked to you, it cannot identify you or retrieve data about you, so such requests generally cannot be fulfilled; records expire automatically. You may complain to your local data protection authority.",
    "Children" to
        "The App is not directed to children under 16, and CatsBlock Corporation does not knowingly collect their personal data. Where local law sets a different minimum age, that age applies. If you are below it, do not use the App.",
    "Regional restrictions" to
        "The service is not available in certain regions because of local laws, sanctions and data-transfer rules, currently including Japan, Iran, Cuba, North Korea, Syria, China, Russia and the Crimea, Donetsk, Luhansk and Sevastopol regions. Cloudflare determines your country from the connection on each request without storing it, and access is re-evaluated automatically. Bypassing these restrictions with a VPN, proxy or other means is prohibited, and you are solely responsible for complying with the laws that apply to you.",
    "Security" to
        "We apply reasonable technical measures such as encrypted connections, request validation and rate limiting, but no system is completely secure, and we cannot guarantee that the service will be free of errors, vulnerabilities or unauthorized access.",
    "Limitation of liability" to
        "The App and its servers are provided \"as is\" and \"as available\", without warranties of any kind. To the fullest extent permitted by applicable law, CatsBlock Corporation is not liable for server downtime or interruptions, data leaks, security breaches, vulnerabilities, errors, inaccurate weather data, or any loss or damage resulting from the use of, or inability to use, the App or its servers, and by using the App you agree not to bring claims against CatsBlock Corporation for these reasons. Weather data accuracy is the responsibility of the third-party providers. Nothing in this policy excludes liability that cannot be excluded by law or limits rights you have under mandatory data protection or consumer laws.",
    "No emergency use" to
        "Do not rely on the App or the Agent for emergencies or for safety-critical or life-critical decisions. Always follow official warnings and instructions from your local authorities.",
    "Indemnification and governing law" to
        "To the fullest extent permitted by law, you agree to indemnify and hold harmless CatsBlock Corporation from claims, losses and expenses arising from your misuse of the App, your breach of this policy or applicable law, or your use of the service from a restricted region. This policy is governed by the laws of the jurisdiction where CatsBlock Corporation is established, without prejudice to mandatory consumer and data protection rules of your country of residence. If a provision is unenforceable, the rest remains in force.",
    "No individual support" to
        "CatsBlock Corporation does not provide individual support and offers no contact channel. Requests, questions or messages cannot be answered.",
    "Changes to this policy" to
        "We may update this policy at any time. Continuing to use the App after an update means you accept it. Last updated: 2026-11-01."
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
