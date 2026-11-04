package com.catsblock.weather.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(settings: DesktopSettings, onChange: (DesktopSettings) -> Unit, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val titles = listOf(t("Language"), t("City"), t("Widget"))
    AppTheme(settings.theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CatsBlock Weather", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(titles[step], fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    Text("${step + 1} / ${titles.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (step) {
                        0 -> LANGUAGES.forEach { (code, label) -> Choice(settings.language == code, label) { onChange(settings.copy(language = code)) } }
                        1 -> {
                            settings.city?.let { Text(it.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                            CitySearch(settings.language) { onChange(settings.withCity(it)) }
                        }
                        else -> {
                            Toggle(t("Show floating widget"), settings.widgetVisible) { onChange(settings.copy(widgetVisible = it)) }
                            Toggle(t("Start with Windows"), settings.startWithWindows) { onChange(settings.copy(startWithWindows = it)) }
                            Spacer(Modifier.height(8.dp))
                            Text(t("Temperature unit"), style = MaterialTheme.typography.labelLarge)
                            Segmented(listOf(false to t("Celsius"), true to t("Fahrenheit")), settings.fahrenheit) { onChange(settings.copy(fahrenheit = it)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step > 0) TextButton(onClick = { step-- }) { Text(t("Back")) } else Spacer(Modifier.height(1.dp))
                    if (step < titles.lastIndex) {
                        Button(onClick = { step++ }, enabled = step != 1 || settings.hasCity) { Text(t("Continue")) }
                    } else {
                        Button(onClick = onFinish) { Text(t("Finish")) }
                    }
                }
            }
        }
    }
}
