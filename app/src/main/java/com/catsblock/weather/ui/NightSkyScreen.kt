package com.catsblock.weather.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.Sky
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NightSkyScreen(vm: WeatherViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val city by vm.city.collectAsStateWithLifecycle()
    val lat = city?.lat ?: 0.0
    val lon = city?.lon ?: 0.0

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    val decl = remember(lat, lon) {
        GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
    }
    val lang = I18n.lang
    val html = remember(lat, lon, lang) { SkyHtml.build(lat, lon, decl) }
    var web by remember(html) { mutableStateOf<WebView?>(null) }
    var ready by remember(html) { mutableStateOf(false) }
    var hasSensor by remember { mutableStateOf(true) }
    var json by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf(true) }
    var galaxies by remember { mutableStateOf(true) }
    var names by remember { mutableStateOf(true) }

    LaunchedEffect(lat, lon) {
        while (true) {
            val fresh = Sky.bodies(lat, lon, System.currentTimeMillis())
            if (fresh != null) {
                json = fresh
                failed = false
                delay(300_000L)
            } else {
                failed = json == null
                delay(15_000L)
            }
        }
    }

    LaunchedEffect(json, ready, web) {
        val j = json
        if (j != null && ready) web?.evaluateJavascript("setBodies($j)", null)
    }

    DisposableEffect(web, ready) {
        val w = web
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()
                if (now - last < 25L) return
                last = now
                val m = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(m, event.values)
                w?.evaluateJavascript("setPose(${m.joinToString(",")})", null)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor == null) {
            hasSensor = false
        } else if (w != null && ready) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm.unregisterListener(listener) }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF02040A), Color(0xFF0B1220)))
        )
    ) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        runCatching {
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        key(html) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        setBackgroundColor(AColor.TRANSPARENT)
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                ready = true
                            }
                        }
                        loadDataWithBaseURL("https://catsblock.app/", html, "text/html", "utf-8", null)
                        web = this
                    }
                },
                onRelease = {
                    it.loadUrl("about:blank")
                    it.destroy()
                }
            )
        }

        IconButton(onClick = vm::back, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), tint = Color.White)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .background(Color(0x99000000), RoundedCornerShape(24.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("Night sky"), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !hasSensor -> tr("Rotation sensor not available.")
                    failed -> tr("Could not load the sky data.")
                    json == null -> tr("Loading sky…")
                    else -> tr("Point your phone at the sky to see the Moon, planets, stars and galaxies.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = lines,
                    onClick = {
                        lines = !lines
                        web?.evaluateJavascript("setLayer('lines',$lines)", null)
                    },
                    label = { Text(tr("Constellations")) },
                    colors = skyChipColors()
                )
                FilterChip(
                    selected = galaxies,
                    onClick = {
                        galaxies = !galaxies
                        web?.evaluateJavascript("setLayer('galaxies',$galaxies)", null)
                    },
                    label = { Text(tr("Galaxies")) },
                    colors = skyChipColors()
                )
                FilterChip(
                    selected = names,
                    onClick = {
                        names = !names
                        web?.evaluateJavascript("setLayer('labels',$names)", null)
                    },
                    label = { Text(tr("Labels")) },
                    colors = skyChipColors()
                )
            }
            if (!granted) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(tr("Allow camera")) }
            }
        }
    }
}
