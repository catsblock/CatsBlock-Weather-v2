package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.data.City
import com.catsblock.weather.i18n.tr

@Composable
internal fun skyChipColors() = FilterChipDefaults.filterChipColors(
    labelColor = Color.White,
    containerColor = Color(0x66000000),
    selectedContainerColor = Color(0xFF2196F3),
    selectedLabelColor = Color.White
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EarthWeb(city: City, interactive: Boolean, modifier: Modifier, onWeb: (WebView) -> Unit = {}) {
    val loading = tr("Loading Earth…")
    val html = remember(city.id, interactive, loading) { EarthHtml.build(city.lat, city.lon, interactive, loading) }
    key(html) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(AColor.TRANSPARENT)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL("https://catsblock.app/", html, "text/html", "utf-8", null)
                    onWeb(this)
                }
            },
            onRelease = {
                it.loadUrl("about:blank")
                it.destroy()
            }
        )
    }
}

@Composable
fun EarthCard(city: City, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    GlassCard(modifier.fillMaxWidth()) {
        CardHeader(Icons.Rounded.Public, tr("Earth"))
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF02050C))
        ) {
            EarthWeb(city, false, Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().clickable { open = true })
        }
    }
    if (open) EarthFull(city) { open = false }
}

@Composable
private fun EarthFull(city: City, onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var clouds by remember { mutableStateOf(true) }
    var rain by remember { mutableStateOf(true) }
    var borders by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            EarthWeb(city, true, Modifier.fillMaxSize()) { web = it }
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color(0x99000000), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, tr("Close"), tint = Color.White)
                }
                Text(
                    "NASA · RainViewer · Natural Earth",
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = clouds,
                        onClick = {
                            clouds = !clouds
                            web?.evaluateJavascript("setLayer('clouds',$clouds)", null)
                        },
                        label = { Text(tr("Clouds")) },
                        colors = skyChipColors()
                    )
                    FilterChip(
                        selected = rain,
                        onClick = {
                            rain = !rain
                            web?.evaluateJavascript("setLayer('rain',$rain)", null)
                        },
                        label = { Text(tr("Rain")) },
                        colors = skyChipColors()
                    )
                    FilterChip(
                        selected = borders,
                        onClick = {
                            borders = !borders
                            web?.evaluateJavascript("setLayer('borders',$borders)", null)
                        },
                        label = { Text(tr("Borders")) },
                        colors = skyChipColors()
                    )
                }
            }
        }
    }
}
