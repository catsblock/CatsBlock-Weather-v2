package com.catsblock.weather.sound

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.catsblock.weather.WeatherApp
import com.catsblock.weather.data.Condition
import com.catsblock.weather.media.MediaCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BASE = "https://actions.google.com/sounds/v1/weather/"
private const val RAIN_ASSET = "rain-sound.mp3"

private data class Track(val asset: String?, val urls: List<String>, val volume: Float)

private fun trackFor(kind: Condition): Track = when (kind) {
    Condition.Clear -> Track(null, listOf(BASE + "wind.ogg"), 0.25f)
    Condition.FewClouds -> Track(null, listOf(BASE + "wind.ogg"), 0.30f)
    Condition.MoreClouds -> Track(null, listOf(BASE + "wind.ogg"), 0.40f)
    Condition.Cloudy -> Track(null, listOf(BASE + "wind_quiet_creaks.ogg", BASE + "wind.ogg"), 0.40f)
    Condition.Fog -> Track(null, listOf(BASE + "rumbling_wind.ogg", BASE + "wind.ogg"), 0.30f)
    Condition.Drizzle -> Track(RAIN_ASSET, emptyList(), 0.55f)
    Condition.Rain -> Track(RAIN_ASSET, emptyList(), 0.80f)
    Condition.Storm -> Track(RAIN_ASSET, emptyList(), 1.00f)
    Condition.Sleet, Condition.Hail -> Track(RAIN_ASSET, emptyList(), 0.50f)
    Condition.Snow, Condition.HeavySnow -> Track(null, listOf(BASE + "strong_wind.ogg", BASE + "wind.ogg"), 0.30f)
    Condition.Blizzard -> Track(null, listOf(BASE + "strong_wind.ogg", BASE + "wind.ogg"), 0.55f)
}

class WeatherSoundPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var current: Condition? = null

    fun play(kind: Condition) {
        if (current == kind && (player != null || job?.isActive == true)) return
        stop()
        current = kind
        val track = trackFor(kind)
        job = scope.launch {
            val app = WeatherApp.instance
            val mp = MediaPlayer()
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (track.asset != null) {
                    app.assets.openFd(track.asset).use {
                        mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                    }
                } else {
                    val file = MediaCache.fetch(app, track.urls) ?: return@launch
                    if (current != kind) return@launch
                    mp.setDataSource(file.absolutePath)
                }
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                withContext(Dispatchers.IO) { mp.prepare() }
                mp.start()
                for (i in 1..20) {
                    val v = track.volume * i / 20f
                    mp.setVolume(v, v)
                    delay(100)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                runCatching { mp.release() }
                if (player === mp) player = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        current = null
    }
}
