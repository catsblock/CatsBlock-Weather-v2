package com.catsblock.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.Cities
import com.catsblock.weather.data.City

fun flagEmoji(code: String): String {
    if (code.length != 2) return "\uD83C\uDF10"
    val upper = code.uppercase()
    if (upper.any { it !in 'A'..'Z' }) return "\uD83C\uDF10"
    val first = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
    val second = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
    return String(first) + String(second)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerScreen(vm: WeatherViewModel) {
    val text by vm.queryText.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val loading by vm.searchLoading.collectAsStateWithLifecycle()
    val message by vm.searchMessage.collectAsStateWithLifecycle()
    val searching = text.trim().length >= 2

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a location") },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = text,
                onValueChange = vm::onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search for a city") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                }
            )
            when {
                searching && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(56.dp))
                }
                searching -> {
                    if (results.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                            Text(message ?: "Keep typing to search", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(results) { city -> CityRow(city) { vm.selectCity(city) } }
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    Cities.continents.forEach { continent ->
                        item(key = continent.title) {
                            Text(
                                continent.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(continent.cities, key = { it.id }) { city -> CityRow(city) { vm.selectCity(city) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityRow(city: City, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(city.name) },
        supportingContent = if (city.region.isNotBlank()) ({ Text(city.region) }) else null,
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(flagEmoji(city.code), style = MaterialTheme.typography.titleLarge)
                Text(city.country, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
