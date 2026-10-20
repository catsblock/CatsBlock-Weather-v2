package com.catsblock.weather.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.catsblock.weather.data.Condition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.max
import kotlin.math.sin

private const val SAMPLE_RATE = 22050

private data class SoundProfile(val wind: Float, val rain: Float, val thunder: Boolean)

private fun profileFor(kind: Condition): SoundProfile = when (kind) {
    Condition.Clear -> SoundProfile(0.08f, 0f, false)
    Condition.FewClouds -> SoundProfile(0.10f, 0f, false)
    Condition.MoreClouds -> SoundProfile(0.15f, 0f, false)
    Condition.Cloudy -> SoundProfile(0.20f, 0f, false)
    Condition.Rain -> SoundProfile(0.15f, 0.6f, false)
    Condition.Storm -> SoundProfile(0.35f, 0.9f, true)
    Condition.Snow -> SoundProfile(0.20f, 0f, false)
    Condition.Fog -> SoundProfile(0.08f, 0f, false)
}

class WeatherSoundPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var current: Condition? = null

    fun play(kind: Condition) {
        if (current == kind && job?.isActive == true) return
        stop()
        current = kind
        val profile = profileFor(kind)
        job = scope.launch { render(profile) }
    }

    fun stop() {
        job?.cancel()
        job = null
        current = null
    }

    private suspend fun render(profile: SoundProfile) {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuffer, SAMPLE_RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            val buffer = ShortArray(2048)
            val random = Random()
            var windLow = 0f
            var rainLow = 0f
            var rumbleLow = 0f
            var gustPhase = 0.0
            var thunderEnvelope = 0f
            while (currentCoroutineContext().isActive) {
                for (i in buffer.indices) {
                    val white = random.nextFloat() * 2f - 1f
                    windLow += 0.015f * (white - windLow)
                    rainLow += 0.35f * (white - rainLow)
                    rumbleLow += 0.003f * (white - rumbleLow)
                    gustPhase += 0.00006
                    val gust = 0.65f + 0.35f * sin(gustPhase).toFloat()
                    var sample = windLow * profile.wind * gust * 10f
                    if (profile.rain > 0f) {
                        sample += (white - rainLow) * profile.rain * 0.5f
                        if (random.nextFloat() < 0.002f) {
                            sample += (random.nextFloat() * 2f - 1f) * profile.rain * 0.8f
                        }
                    }
                    if (profile.thunder) {
                        if (thunderEnvelope < 0.001f && random.nextFloat() < 0.000002f) thunderEnvelope = 1f
                        thunderEnvelope *= 0.99996f
                        sample += rumbleLow * thunderEnvelope * 25f
                    }
                    buffer[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE * 0.8f).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
