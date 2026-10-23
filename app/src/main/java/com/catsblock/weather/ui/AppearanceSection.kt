package com.catsblock.weather.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.ApiSource
import com.catsblock.weather.data.CardId
import com.catsblock.weather.data.CardTheme
import com.catsblock.weather.data.FontPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSection(modifier: Modifier = Modifier) {
    val prefs = LocalUiPrefs.current
    val actions = LocalUiActions.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.importFont(uri)
    }
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Edit cards", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Change color, proportion, position and theme of each card",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = { actions.update { it.copy(edit = !it.edit) } }) { Text(if (prefs.edit) "Done" else "Edit") }
            }
            Text("Default card theme", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardTheme.entries.forEach { t ->
                    FilterChip(prefs.defaultTheme == t, { actions.update { it.copy(defaultTheme = t) } }, { Text(t.label) })
                }
            }
            Text("Font", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontPreset.entries.filter { it != FontPreset.Custom }.forEach { f ->
                    FilterChip(prefs.font == f, { actions.update { it.copy(font = f) } }, { Text(f.label) })
                }
                if (prefs.customPath != null) {
                    FilterChip(
                        prefs.font == FontPreset.Custom,
                        { actions.update { it.copy(font = FontPreset.Custom) } },
                        { Text(prefs.customName ?: "Custom file", maxLines = 1) }
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "font/ttf", "font/otf", "font/sfnt",
                            "application/x-font-ttf", "application/x-font-otf",
                            "application/octet-stream"
                        )
                    )
                },
                Modifier.fillMaxWidth()
            ) { Text("Load font file (.ttf / .otf)") }
            Text("Weather source (free, no key)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiSource.entries.forEach { a ->
                    FilterChip(prefs.api == a, { actions.update { it.copy(api = a) } }, { Text(a.label) })
                }
            }
            Text(
                "Pull to refresh or reopen the app to load data from the new source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { actions.update { it.copy(order = CardId.entries.toList(), styles = emptyMap(), edit = false) } },
                Modifier.fillMaxWidth()
            ) { Text("Reset card layout") }
        }
    }
}
