package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

private enum class RadarLayer(val label: String, val overlay: String) {
    Rain("Rain", "radar"),
    Hail("Hail", "thunder"),
    Snow("Snow & Freezing Rain", "snowAccu"),
    Wind("Wind Speed & Direction", "wind"),
    Rotational("Rotational Winds", "gust"),
    Updrafts("Updrafts & Downdrafts", "cape"),
    Tornadoes("Tornadoes", "ctop"),
    Fog("Fog", "rh"),
    Clouds("Clouds", "clouds")
}

private fun layerUrl(layer: RadarLayer, lat: Double, lon: Double): String {
    val la = String.format(Locale.US, "%.4f", lat)
    val lo = String.format(Locale.US, "%.4f", lon)
    return "https://embed.windy.com/embed2.html?lat=$la&lon=$lo&detailLat=$la&detailLon=$lo" +
        "&zoom=6&level=surface&overlay=${layer.overlay}&product=ecmwf&menu=&message=true" +
        "&marker=true&calendar=now&type=map&location=coordinates&detail=&metricWind=km%2Fh&metricTemp=%C2%B0C"
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: WeatherViewModel) {
    val city by vm.city.collectAsStateWithLifecycle()
    var layer by remember { mutableStateOf(RadarLayer.Rain) }
    val lat = city?.lat ?: 0.0
    val lon = city?.lon ?: 0.0
    val url = layerUrl(layer, lat, lon)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radar map") },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(RadarLayer.entries.toList()) { item ->
                    FilterChip(
                        selected = item == layer,
                        onClick = { layer = item },
                        label = { Text(item.label) }
                    )
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                    }
                },
                update = { view ->
                    if (view.tag != url) {
                        view.tag = url
                        view.loadUrl(url)
                    }
                },
                onRelease = { it.destroy() }
            )
        }
    }
}
