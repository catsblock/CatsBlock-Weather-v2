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

private const val WIND_BASE = "https://actions.google.com/sounds/v1/weather/"
private const val RAIN_URL =
    "https://raw.githubusercontent.com/catsblock/CatsBlock-Weather-v2/main/sounds/rain-sound.mp3"

private data class Track(val urls: List<String>, val volume: Float)

private fun trackFor(kind: Condition): Track = when (kind) {
    Condition.Clear -> Track(listOf(WIND_BASE + "wind.ogg"), 0.25f)
    Condition.FewClouds -> Track(listOf(WIND_BASE + "wind.ogg"), 0.30f)
    Condition.MoreClouds -> Track(listOf(WIND_BASE + "wind.ogg"), 0.40f)
    Condition.Cloudy -> Track(listOf(WIND_BASE + "wind_quiet_creaks.ogg", WIND_BASE + "wind.ogg"), 0.40f)
    Condition.Fog -> Track(listOf(WIND_BASE + "rumbling_wind.ogg", WIND_BASE + "wind.ogg"), 0.30f)
    Condition.Drizzle -> Track(listOf(RAIN_URL), 0.55f)
    Condition.Rain -> Track(listOf(RAIN_URL), 0.80f)
    Condition.Storm -> Track(listOf(RAIN_URL), 1.00f)
    Condition.Sleet, Condition.Hail -> Track(listOf(RAIN_URL), 0.50f)
    Condition.Snow, Condition.HeavySnow -> Track(listOf(WIND_BASE + "strong_wind.ogg", WIND_BASE + "wind.ogg"), 0.30f)
    Condition.Blizzard -> Track(listOf(WIND_BASE + "strong_wind.ogg", WIND_BASE + "wind.ogg"), 0.55f)
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
            val file = MediaCache.fetch(WeatherApp.instance, track.urls) ?: return@launch
            if (current != kind) return@launch
            val mp = MediaPlayer()
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.setDataSource(file.absolutePath)
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
