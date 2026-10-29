package com.catsblock.weather.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlin.math.cos

data class RadarReading(val wet: Boolean, val strength: Double)

object RadarFrames {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: String? = null

    @Volatile
    private var cachedAt = 0L

    suspend fun base(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < 300_000L }?.let { return@withContext it }
        val fresh = runCatching {
            client.newCall(Request.Builder().url("https://api.rainviewer.com/public/weather-maps.json").build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = JSONObject(response.body?.string() ?: return@use null)
                    val host = json.getString("host")
                    val past = json.getJSONObject("radar").getJSONArray("past")
                    if (past.length() == 0) return@use null
                    host + past.getJSONObject(past.length() - 1).getString("path")
                }
        }.getOrNull()
        if (fresh != null) {
            cached = fresh
            cachedAt = now
        }
        fresh ?: cached
    }

    fun tileUrl(base: String, zoom: Int, x: Int, y: Int): String = "$base/256/$zoom/$x/$y/2/1_1.png"

    internal fun http(): OkHttpClient = client
}

object RadarProbe {

    private const val ZOOM = 6
    private const val WINDOW = 3
    private const val SIZE = 256

    suspend fun read(lat: Double, lon: Double): RadarReading? = withContext(Dispatchers.IO) {
        val base = RadarFrames.base() ?: return@withContext null
        val n = 2.0.pow(ZOOM)
        val latRad = lat * PI / 180.0
        val worldX = (lon + 180.0) / 360.0 * n
        val worldY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val tileX = floor(worldX).toInt()
        val tileY = floor(worldY).toInt()
        val px = ((worldX - tileX) * SIZE).toInt().coerceIn(0, SIZE - 1)
        val py = ((worldY - tileY) * SIZE).toInt().coerceIn(0, SIZE - 1)
        runCatching {
            RadarFrames.http().newCall(Request.Builder().url(RadarFrames.tileUrl(base, ZOOM, tileX, tileY)).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return@use null
                    val bitmap = BitmapFactory.decodeStream(response.body?.byteStream()) ?: return@use null
                    var echoes = 0
                    var total = 0
                    for (dy in -WINDOW..WINDOW) {
                        for (dx in -WINDOW..WINDOW) {
                            val x = (px + dx).coerceIn(0, bitmap.width - 1)
                            val y = (py + dy).coerceIn(0, bitmap.height - 1)
                            total++
                            if ((bitmap.getPixel(x, y) ushr 24) > 40) echoes++
                        }
                    }
                    bitmap.recycle()
                    val coverage = echoes.toDouble() / total
                    RadarReading(coverage >= 0.12, coverage)
                }
        }.getOrNull()
    }
}
