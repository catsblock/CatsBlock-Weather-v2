package com.catsblock.weather.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catsblock.weather.data.Condition

private fun skyColors(kind: Condition, isDay: Boolean): List<Color> = when (kind) {
    Condition.Clear -> if (isDay) listOf(Color(0xFF2F80ED), Color(0xFF8EC5FC)) else listOf(Color(0xFF0B1026), Color(0xFF283B6E))
    Condition.FewClouds -> if (isDay) listOf(Color(0xFF3F8AE0), Color(0xFF9CCBF5)) else listOf(Color(0xFF0E1530), Color(0xFF2D3F70))
    Condition.MoreClouds -> if (isDay) listOf(Color(0xFF5F7C99), Color(0xFFA7B8C8)) else listOf(Color(0xFF1A2236), Color(0xFF3A4660))
    Condition.Cloudy -> if (isDay) listOf(Color(0xFF6C7A89), Color(0xFFB0BAC4)) else listOf(Color(0xFF222B38), Color(0xFF47515F))
    Condition.Rain -> if (isDay) listOf(Color(0xFF3E4C5E), Color(0xFF7A8896)) else listOf(Color(0xFF141B26), Color(0xFF2E3846))
    Condition.Storm -> listOf(Color(0xFF1C2230), Color(0xFF4A5366))
    Condition.Snow -> if (isDay) listOf(Color(0xFF8FA3B8), Color(0xFFDCE6F0)) else listOf(Color(0xFF2B3646), Color(0xFF5B6B80))
    Condition.Fog -> if (isDay) listOf(Color(0xFF8C9AA5), Color(0xFFCBD3D9)) else listOf(Color(0xFF2B3238), Color(0xFF566068))
}

private fun keysFor(kind: Condition): List<String> = when (kind) {
    Condition.Clear -> listOf("clear")
    Condition.FewClouds -> listOf("few_clouds", "clear")
    Condition.MoreClouds -> listOf("more_clouds", "cloudy")
    Condition.Cloudy -> listOf("cloudy", "more_clouds")
    Condition.Rain -> listOf("rain")
    Condition.Storm -> listOf("storm", "rain")
    Condition.Snow -> listOf("snow")
    Condition.Fog -> listOf("fog", "cloudy")
}

private fun findVideo(context: Context, kind: Condition, isDay: Boolean): Int? {
    val period = if (isDay) "day" else "night"
    val names = buildList {
        keysFor(kind).forEach {
            add("weather_${it}_$period")
            add("weather_$it")
        }
        add("weather_default_$period")
        add("weather_default")
    }
    for (name in names) {
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        if (id != 0) return id
    }
    return null
}

private class VideoHolder(private val context: Context, private val resId: Int) {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var inForeground = true

    val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            surface = Surface(texture)
            start()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            stopPlayer()
            surface?.release()
            surface = null
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    private fun start() {
        val target = surface ?: return
        stopPlayer()
        try {
            val mp = MediaPlayer()
            context.resources.openRawResourceFd(resId).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setSurface(target)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                prepared = true
                it.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                sync()
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
            player = mp
        } catch (_: Exception) {
            player = null
        }
    }

    fun setForeground(value: Boolean) {
        inForeground = value
        sync()
    }

    private fun sync() {
        val p = player ?: return
        if (!prepared) return
        runCatching {
            if (inForeground) {
                if (!p.isPlaying) p.start()
            } else if (p.isPlaying) {
                p.pause()
            }
        }
    }

    private fun stopPlayer() {
        prepared = false
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    fun release() {
        stopPlayer()
        surface?.release()
        surface = null
    }
}

@Composable
private fun VideoLayer(resId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember(resId) { VideoHolder(context.applicationContext, resId) }
    DisposableEffect(lifecycleOwner, holder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.setForeground(true)
                Lifecycle.Event.ON_STOP -> holder.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = holder.listener
            }
        }
    )
}

@Composable
fun WeatherBackground(kind: Condition, isDay: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember(kind, isDay) { findVideo(context, kind, isDay) }
    Box(modifier) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(skyColors(kind, isDay))))
        if (resId != null) {
            VideoLayer(resId, Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.22f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.35f)
                )
            )
        )
    }
}
