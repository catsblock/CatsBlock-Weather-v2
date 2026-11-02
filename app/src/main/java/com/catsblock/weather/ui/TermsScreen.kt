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

private val termsSections = listOf(
    "Acceptance of the terms" to
        "By using CatsBlock Weather (the \"App\"), published by CatsBlock Corporation, you agree to these Terms of Service. If you do not agree, do not use the App. You can review these terms at any time in Settings.",
    "Who can use the App" to
        "You must be at least 6 years old to use the App. The Agent is available only from age 13, and users from 13 to 18 are offered family controls with a daily time limit. If you use the App on behalf of a child, you are responsible for their use.",
    "The Agent" to
        "The Agent is an AI that answers questions about the weather. Its replies are generated automatically, may be inaccurate or incomplete, and are not professional weather, safety or emergency advice. Always check the official forecast and warnings from your local authorities.",
    "Agent usage limits" to
        "Agent usage is limited by the number of tokens it spends, up to 2,500 tokens per device within 24 hours. When the limit is reached, the message box is replaced by a notice with the time it will return. Messages sent in quick succession are queued, and sending too many messages in a short time blocks further messages for one minute.",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(vm: WeatherViewModel) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("Terms of Service")) },
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
                    termsSections.forEachIndexed { index, (title, body) ->
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
