package com.catsblock.weather.sound

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.catsblock.weather.WeatherApp
import com.catsblock.weather.data.Condition
import com.catsblock.weather.media.MediaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BASE = "https://actions.google.com/sounds/v1/weather/"

private data class Track(val urls: List<String>, val volume: Float)

private fun trackFor(kind: Condition): Track = when (kind) {
    Condition.Clear -> Track(listOf(BASE + "wind.ogg"), 0.25f)
    Condition.FewClouds -> Track(listOf(BASE + "wind.ogg"), 0.30f)
    Condition.MoreClouds -> Track(listOf(BASE + "wind.ogg"), 0.40f)
    Condition.Cloudy -> Track(listOf(BASE + "wind_quiet_creaks.ogg", BASE + "wind.ogg"), 0.40f)
    Condition.Rain -> Track(listOf(BASE + "light_rain.ogg", BASE + "rain_heavy_loud.ogg"), 0.75f)
    Condition.Storm -> Track(listOf(BASE + "thunderstorm.ogg", BASE + "rain_heavy_loud.ogg"), 0.85f)
    Condition.Snow -> Track(listOf(BASE + "strong_wind.ogg", BASE + "wind.ogg"), 0.30f)
    Condition.Fog -> Track(listOf(BASE + "rumbling_wind.ogg", BASE + "wind.ogg"), 0.30f)
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
            } catch (_: Exception) {
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
