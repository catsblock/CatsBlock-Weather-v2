package com.catsblock.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedScroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: WeatherViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
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
            GroupLabel("Units")
            Group {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Temperature", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !settings.fahrenheit,
                            onClick = { vm.setFahrenheit(false) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Celsius") }
                        SegmentedButton(
                            selected = settings.fahrenheit,
                            onClick = { vm.setFahrenheit(true) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Fahrenheit") }
                    }
                }
            }

            GroupLabel("Updates and sounds")
            Group {
                SwitchRow(
                    "Mobile data updates",
                    "Allow weather updates over mobile data. When off, updates only run on Wi-Fi.",
                    settings.mobileData,
                    vm::setMobileData
                )
                GroupDivider()
                SwitchRow(
                    "Weather sounds",
                    "Play ambient sounds that match the current weather.",
                    settings.sounds,
                    vm::setSounds
                )
            }

            GroupLabel("Privacy")
            Group {
                InfoRow(
                    "Location permission",
                    "The Locate button asks for approximate or precise location. Your position is only used to find the weather where you are. It is stored on your device as a saved city and is never sent to any account or server operated by this app."
                )
                GroupDivider()
                InfoRow(
                    "Network access",
                    "To show the weather, the coordinates of your selected city are sent to Open-Meteo. The radar map is loaded from Windy and may collect data under its own policy. Weather icons are downloaded from GitHub."
                )
                GroupDivider()
                InfoRow(
                    "Data stored on your device",
                    "Saved cities, and your unit and sound choices are stored locally. The app has no accounts, no analytics and no advertising. Clearing the app data removes everything."
                )
                GroupDivider()
                InfoRow(
                    "Permissions used",
                    "Internet, network state, approximate location and precise location."
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun InfoRow(title: String, description: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
