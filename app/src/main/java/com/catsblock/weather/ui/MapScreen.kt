package com.catsblock.weather.ui

import com.catsblock.weather.ui.components.ExpressiveProgressBar
import com.catsblock.weather.ui.components.ExpressiveLoader
import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.i18n.tr
import java.util.Locale

internal enum class RadarLayer(val label: String, val overlay: String) {
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
internal object RadarWebView {
    private var view: WebView? = null
    private var loadedUrl: String? = null
    private var loadedPlace: String? = null

    var progress by mutableIntStateOf(0)
        private set

    fun obtain(context: Context): WebView {
        view?.let { return it }
        val web = WebView(context.applicationContext)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(webView: WebView?, newProgress: Int) {
                progress = newProgress
            }
        }
        val dm = context.resources.displayMetrics
        web.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
        )
        web.layout(0, 0, dm.widthPixels, dm.heightPixels)
        view = web
        return web
    }

    fun show(context: Context, layer: RadarLayer, lat: Double, lon: Double) {
        val web = obtain(context)
        val url = layerUrl(layer, lat, lon)
        if (loadedUrl != url) {
            loadedUrl = url
            loadedPlace = place(lat, lon)
            progress = 0
            web.loadUrl(url)
        }
    }

    fun prewarm(context: Context, lat: Double, lon: Double) {
        if (loadedPlace != place(lat, lon)) show(context, RadarLayer.Rain, lat, lon)
    }

    fun detach(web: WebView) {
        (web.parent as? ViewGroup)?.removeView(web)
        if (progress >= 100) web.onPause()
    }

    private fun place(lat: Double, lon: Double): String = String.format(Locale.US, "%.2f,%.2f", lat, lon)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: WeatherViewModel) {
    val city by vm.city.collectAsStateWithLifecycle()
    var layer by remember { mutableStateOf(RadarLayer.Rain) }
    val context = LocalContext.current
    val lat = city?.lat ?: 0.0
    val lon = city?.lon ?: 0.0

    LaunchedEffect(layer, lat, lon) {
        RadarWebView.show(context, layer, lat, lon)
    }
    val progress = RadarWebView.progress

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr("Radar map"), style = MaterialTheme.typography.titleLarge)
                        city?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) }
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
                        label = { Text(tr(item.label)) }
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        RadarWebView.obtain(ctx).also {
                            (it.parent as? ViewGroup)?.removeView(it)
                            it.onResume()
                        }
                    },
                    onRelease = { RadarWebView.detach(it) }
                )
                if (progress < 100) {
                    ExpressiveProgressBar(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (progress < 35) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveLoader(size = 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(tr("Loading radar…"), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}
