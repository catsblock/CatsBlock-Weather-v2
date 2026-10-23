package com.catsblock.weather.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.Moon
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun normalize(deg: Double): Double = (deg % 360.0 + 360.0) % 360.0

private val LiveGlass = Color(0x66101826)
private val LiveSoft = Color(0xCCFFFFFF)

private fun Modifier.liveGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(LiveGlass)
    .border(1.dp, Color.White.copy(alpha = 0.18f), shape)

@Composable
private fun LiveStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LiveSoft, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
    }
}

@Composable
fun LiveMoonScreen(vm: WeatherViewModel) {
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

    var rot by remember { mutableStateOf<FloatArray?>(null) }
    var hasSensor by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val matrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                rot = matrix
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) else hasSensor = false
        onDispose { sm.unregisterListener(listener) }
    }

    val pulse by rememberInfiniteTransition(label = "reticle").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "reticlePulse"
    )

    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val pos = remember(nowMs / 1000L, lat, lon) { Moon.position(nowMs, lat, lon) }
    val phase = remember(nowMs / 60_000L) { Moon.phase(nowMs) }
    val declination = remember(lat, lon) {
        GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
    }
    val altDeg = Math.toDegrees(pos.altitude)
    val azDeg = normalize(Math.toDegrees(pos.azimuth))
    val azMag = pos.azimuth - Math.toRadians(declination)

    var xc = 0.0
    var yc = 0.0
    var zc = 0.0
    var lookAz: Double? = null
    val m = rot
    if (m != null) {
        val ca = cos(pos.altitude)
        val mx = ca * sin(azMag)
        val my = ca * cos(azMag)
        val mz = sin(pos.altitude)
        xc = mx * m[0] + my * m[3] + mz * m[6]
        yc = mx * m[1] + my * m[4] + mz * m[7]
        zc = -(mx * m[2] + my * m[5] + mz * m[8])
        lookAz = normalize(Math.toDegrees(atan2((-m[2]).toDouble(), (-m[5]).toDouble())) + declination)
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF1B2A4A)))
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

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.28f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f)
                    )
                )
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val tanX = 0.466
            val tanY = 0.625
            val inFront = m != null && zc > 0.05
            val sx = if (inFront) w / 2f + (xc / zc / tanX * w / 2f).toFloat() else 0f
            val sy = if (inFront) h / 2f - (yc / zc / tanY * h / 2f).toFloat() else 0f
            val visible = inFront && abs(sx - w / 2f) < w / 2f && abs(sy - h / 2f) < h / 2f
            val half = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }

            if (m != null && !visible) {
                val arrowAngle = Math.toDegrees(atan2(xc, yc)).toFloat()
                Canvas(Modifier.fillMaxSize()) {
                    val reach = min(size.width, size.height) * 0.34f
                    val dx = sin(Math.toRadians(arrowAngle.toDouble())).toFloat()
                    val dy = -cos(Math.toRadians(arrowAngle.toDouble())).toFloat()
                    val center = Offset(size.width / 2f + dx * reach, size.height / 2f + dy * reach)
                    drawCircle(Color(0x33101826), 46.dp.toPx(), center)
                    drawCircle(Color.White.copy(alpha = 0.22f), 46.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                    rotate(arrowAngle, center) {
                        val path = Path().apply {
                            moveTo(center.x, center.y - 34.dp.toPx())
                            lineTo(center.x + 22.dp.toPx(), center.y + 20.dp.toPx())
                            lineTo(center.x, center.y + 8.dp.toPx())
                            lineTo(center.x - 22.dp.toPx(), center.y + 20.dp.toPx())
                            close()
                        }
                        drawPath(path, Color.White.copy(alpha = 0.9f))
                    }
                }
            }

            if (visible) {
                Box(
                    Modifier
                        .offset { IntOffset((sx - half).roundToInt(), (sy - half).roundToInt()) }
                        .size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val sc = 1f + pulse * 0.55f
                                scaleX = sc
                                scaleY = sc
                                alpha = 0.7f * (1f - pulse)
                            }
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    MoonImage(
                        nowMs,
                        phase.cycle,
                        Modifier
                            .fillMaxSize()
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                }
            }
        }

        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .liveGlass(CircleShape)
                    .clickable(onClick = vm::back),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .height(36.dp)
                    .liveGlass(RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .graphicsLayer {
                                val sc = 1f + pulse * 1.4f
                                scaleX = sc
                                scaleY = sc
                                alpha = 0.6f * (1f - pulse)
                            }
                            .clip(CircleShape)
                            .background(Color(0xFFFF5A5F))
                    )
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5A5F)))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("LIVE moon"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }

        SkyMap(
            moonAz = azDeg,
            moonAlt = altDeg,
            lookAz = lookAz,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(136.dp)
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .liveGlass(RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr(Moon.phaseName(phase.cycle)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        "${(phase.illumination * 100).roundToInt()}% ${tr("illuminated")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiveSoft
                    )
                }
                MoonOrb(phase.cycle, Modifier.size(60.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveStat(tr("Altitude"), "${altDeg.roundToInt()}°", Modifier.weight(1f))
                LiveStat(tr("Azimuth"), "${azDeg.roundToInt()}° ${compassLabel(azDeg)}", Modifier.weight(1f))
                lookAz?.let {
                    LiveStat(tr("Camera"), "${it.roundToInt()}° ${compassLabel(it)}", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    !hasSensor -> tr("Rotation sensor not available.")
                    altDeg < 0 -> tr("The Moon is below the horizon right now.")
                    m == null -> tr("Move your phone to calibrate.")
                    else -> tr("Point your phone at the sky and follow the arrow.")
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = LiveSoft,
                textAlign = TextAlign.Center
            )
            if (!granted) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Allow camera"))
                }
            }
        }
    }
}

@Composable
private fun SkyMap(moonAz: Double, moonAlt: Double, lookAz: Double?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.liveGlass(CircleShape)) {
        val r = min(size.width, size.height) / 2f - 14.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        val line = Color.White.copy(alpha = 0.35f)
        listOf(1f, 2f / 3f, 1f / 3f).forEach { drawCircle(line, r * it, c, style = Stroke(1.dp.toPx())) }
        drawLine(line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        drawLine(line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        val style = TextStyle(color = Color.White, fontSize = 10.sp)
        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (label, deg) ->
            val rad = Math.toRadians(deg)
            val p = Offset(c.x + (r + 8.dp.toPx()) * sin(rad).toFloat(), c.y - (r + 8.dp.toPx()) * cos(rad).toFloat())
            drawText(measurer, label, Offset(p.x - 4.dp.toPx(), p.y - 7.dp.toPx()), style)
        }
        lookAz?.let { az ->
            val a1 = Math.toRadians(az - 25.0)
            val a2 = Math.toRadians(az + 25.0)
            val cone = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + r * sin(a1).toFloat(), c.y - r * cos(a1).toFloat())
                lineTo(c.x + r * sin(a2).toFloat(), c.y - r * cos(a2).toFloat())
                close()
            }
            drawPath(cone, Color(0x5590CAF9))
        }
        val rho = if (moonAlt < 0) r else (r * (1.0 - moonAlt / 90.0)).toFloat()
        val rad = Math.toRadians(moonAz)
        val p = Offset(c.x + rho * sin(rad).toFloat(), c.y - rho * cos(rad).toFloat())
        if (moonAlt < 0) {
            drawCircle(Color.White.copy(alpha = 0.6f), 6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        } else {
            drawCircle(Color(0x55FFF3C4), 11.dp.toPx(), p)
            drawCircle(Color(0xFFFFF3C4), 6.dp.toPx(), p)
        }
    }
}
