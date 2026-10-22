package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
}
