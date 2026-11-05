package com.catsblock.weather.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.net.URI

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
        "When the WeatherAPI.com, Visual Crossing or OpenWeather data source or the Agent is used, requests go through a Cloudflare Worker operated by CatsBlock Corporation, which keeps API keys off your device and limits abuse. To apply limits separately to each device, even when several devices share the same network or IP address, the App creates a one-way hash on your device from general hardware characteristics (manufacturer, model, board, processor details, screen size and Android version) and sends only that hash. The Worker combines it with a one-way hash of your IP address, and the counters tied to the result are kept in a Durable Object and Cloudflare KV for at most 24 hours. The original IP address and the raw hardware details are never stored. The hash is not an advertising identifier, is not linked to any account or person, and devices with identical hardware on the same network may share a limit. A network-wide counter, also based on a one-way hash of the IP address, helps prevent abuse. Your country, derived by Cloudflare from the connection, is checked per request and not stored. Weather responses for rounded coordinates are cached for about 15 minutes. Cloudflare may process connection data under its own terms. The Open-Meteo source does not use this proxy.",
    "AI Agent" to
        "The Agent sends your message, the selected city and rounded coordinates through the Worker to Google's Gemini API and public weather services, which process them under their own terms. To answer, the Agent can read the weather data of the selected place, including the recent history and the next hours of temperature, humidity, wind, pressure, cloud cover, UV index and precipitation, and can show cards, charts and a map layer in its reply; the map is loaded on your device from public map providers. Do not enter personal, sensitive or confidential information. Replies are AI-generated, may be inaccurate and are not professional weather, safety or emergency advice; no decision with legal or similarly significant effects is made about you. CatsBlock Corporation does not store your messages or replies. The activity panel shows real technical steps, not the AI's reasoning.",
    "International transfers" to
        "Data is processed on Cloudflare's global network and by the third-party providers named in this policy, which may be located outside your country, including outside the EEA and the UK. Where required, transfers rely on the safeguards those providers offer, such as Standard Contractual Clauses.",
    "Your rights" to
        "Depending on where you live (including under the GDPR, UK GDPR, LGPD, CCPA/CPRA and PIPEDA), you may have rights to access, correct, delete, restrict or port personal data, to object to processing and to withdraw consent. CatsBlock Corporation does not sell or share personal information and does not use it for advertising or profiling. Because it offers no accounts or contact channel and keeps only irreversible hashes that cannot be linked to you, it cannot identify you or retrieve data about you, so such requests generally cannot be fulfilled; records expire automatically. You may complain to your local data protection authority.",
    "Children" to
        "CatsBlock Weather is not directed to children. The Agent is available to all users, and no age is collected, stored, or used to restrict features.",
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

private val termsSections = listOf(
    "Acceptance of the terms" to
        "By using CatsBlock Weather (the \"App\"), published by CatsBlock Corporation, you agree to these Terms of Service. If you do not agree, do not use the App. You can review these terms at any time in Settings.",
    "Who can use the App" to
        "You must comply with the laws that apply where you live to use the App. The Agent is available to all users, and you agree not to use it for unlawful purposes.",
    "The Agent" to
        "The Agent is an AI that answers questions about the weather. Its replies are generated automatically, may be inaccurate or incomplete, and are not professional weather, safety or emergency advice. Always check the official forecast and warnings from your local authorities.",
    "Agent usage limits" to
        "Agent usage is limited by the number of tokens it spends, up to 30,000 tokens per device within 5 hours. When the limit is reached, the message box is replaced by a notice with the time it will return. Messages sent in quick succession are queued, and sending too many messages in a short time blocks further messages for one minute.",
    "Acceptable use" to
        "Do not use the Agent for anything inappropriate, unlawful or against these terms, including crimes, harassment, attempts to bypass its rules or safety measures, and requests unrelated to the weather. Messages that violate these terms are not answered and are marked as a violation. Do not attempt to bypass limits, impersonate other devices, overload the servers or access the service by automated means.",
    "Servers and third-party services" to
        "The App relies on servers and services operated by CatsBlock Corporation and third parties, including a Cloudflare Worker, Google's Gemini and public weather and map providers, which follow their own terms. To enforce fair limits for each device, a one-way hash of your device's hardware characteristics is used as described in the Privacy Policy. We may block or limit access to prevent abuse or to comply with the law.",
    "Availability and regions" to
        "The service is provided when available and may change, be interrupted or be unavailable in some regions because of local laws, sanctions and data-transfer rules. We may add, change or remove features, limits and data sources at any time.",
    "Disclaimer" to
        "The App and the Agent are provided \"as is\" and \"as available\", without warranties of any kind. Do not rely on them for emergencies or for safety-critical or life-critical decisions.",
    "Limitation of liability" to
        "To the fullest extent permitted by law, CatsBlock Corporation is not liable for any indirect, incidental or consequential damages, or for decisions made based on the App or the Agent.",
    "Suspension and termination" to
        "We may suspend or block access to the App or the Agent, including for violations of these terms. You can stop using the App at any time by uninstalling it.",
    "Changes to these terms" to
        "We may update these terms at any time. Continuing to use the App after an update means you accept it. Last updated: 2026-11-01."
)

@Composable
fun LegalPage(title: String, sections: List<Pair<String, String>>, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(SmallShape).clickable(onClick = onBack).padding(8.dp)) { GlyphIcon(Glyph.Back, scheme.onSurface) }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Card(Modifier.fillMaxWidth()) {
            sections.forEachIndexed { index, (heading, text) ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Text(t(heading), style = MaterialTheme.typography.titleMedium)
                Text(t(text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PrivacyPage(onBack: () -> Unit) = LegalPage(t("Privacy Policy"), policySections, onBack)

@Composable
fun TermsPage(onBack: () -> Unit) = LegalPage(t("Terms of Service"), termsSections, onBack)

fun openLink(url: String) {
    runCatching {
        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
            java.awt.Desktop.getDesktop().browse(URI.create(url))
        }
    }
}

@Composable
fun AboutPage(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val build = remember { WindowsUpdater.installedBuild() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(SmallShape).clickable(onClick = onBack).padding(8.dp)) { GlyphIcon(Glyph.Back, scheme.onSurface) }
            Text(t("About this app"), style = MaterialTheme.typography.titleLarge)
        }
        Card(Modifier.fillMaxWidth()) {
            Text("CatsBlock Weather", style = MaterialTheme.typography.headlineSmall)
            Text(t("App version") + " 2.0.$build", color = scheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth(), t("Company information")) {
            Text("CatsBlock Corporation", style = MaterialTheme.typography.titleMedium)
            Text(t("Publisher of CatsBlock Weather."), color = scheme.onSurfaceVariant)
            Text(t("Individual support is not available and no contact channel is provided."), color = scheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth().clickable { openLink("https://github.com/catsblock/CatsBlock-Weather-v2") }, t("Project code")) {
            Text("github.com/catsblock/CatsBlock-Weather-v2", color = scheme.primary)
        }
    }
}
