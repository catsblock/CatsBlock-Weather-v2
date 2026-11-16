package com.catsblock.weather.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

internal data class DeviceOrientation(
    val heading: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val available: Boolean = false
)

private object DeviceOrientationStream : SensorEventListener {
    private val lock = Any()
    private val mutable = MutableStateFlow(DeviceOrientation())
    val state = mutable.asStateFlow()
    private var references = 0
    private var manager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var windowManager: WindowManager? = null
    private var filteredHeading: Float? = null
    private var filteredPitch: Float? = null
    private var filteredRoll: Float? = null

    fun acquire(context: Context): AutoCloseable {
        synchronized(lock) {
            references++
            if (references == 1) {
                manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val sensorManager = manager
                val sensor = rotationSensor
                if (sensorManager != null && sensor != null) {
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }
        return AutoCloseable { release() }
    }

    private fun release() {
        synchronized(lock) {
            if (references == 0) return
            references--
            if (references == 0) {
                manager?.unregisterListener(this)
                manager = null
                rotationSensor = null
                windowManager = null
                filteredHeading = null
                filteredPitch = null
                filteredRoll = null
                mutable.value = DeviceOrientation()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9)
        val screenMatrix = FloatArray(9)
        val angles = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val (axisX, axisY) = when (windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(matrix, axisX, axisY, screenMatrix)) return
        SensorManager.getOrientation(screenMatrix, angles)
        val heading = (Math.toDegrees(angles[0].toDouble()).toFloat() + 360f) % 360f
        val pitch = Math.toDegrees(angles[1].toDouble()).toFloat()
        val roll = Math.toDegrees(angles[2].toDouble()).toFloat()
        synchronized(lock) {
            val alpha = 0.38f
            val previous = filteredHeading
            filteredHeading = if (previous == null) heading else {
                val delta = ((heading - previous + 540f) % 360f) - 180f
                (previous + delta * alpha + 360f) % 360f
            }
            filteredPitch = filteredPitch?.let { it + (pitch - it) * alpha } ?: pitch
            filteredRoll = filteredRoll?.let { it + (roll - it) * alpha } ?: roll
            mutable.value = DeviceOrientation(filteredHeading ?: heading, filteredPitch ?: pitch, filteredRoll ?: roll, true)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
internal fun rememberDeviceOrientation(): DeviceOrientation {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reading by DeviceOrientationStream.state.collectAsStateWithLifecycle()
    DisposableEffect(context, lifecycleOwner) {
        var lease: AutoCloseable? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (lease == null) lease = DeviceOrientationStream.acquire(context)
                Lifecycle.Event.ON_STOP -> {
                    lease?.close()
                    lease = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lease = DeviceOrientationStream.acquire(context)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            lease?.close()
        }
    }
    return reading
}

internal fun magneticDeclination(latitude: Double, longitude: Double): Float =
    android.hardware.GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()).declination

internal fun shortestAngle(degrees: Float): Float = ((degrees + 540f) % 360f) - 180f
