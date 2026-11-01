package com.catsblock.weather.data

import com.catsblock.weather.ui.developer.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RegionGuard {

    private val state = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .retryOnConnectionFailure(false)
        .build()

    fun report() {
        state.value = true
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext
        try {
            val request = Request.Builder()
                .url("$base/v1/region")
                .header("Cache-Control", "no-cache")
                .header("Connection", "close")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val root = JSONObject(response.body?.string().orEmpty())
                if (root.has("allowed")) state.value = !root.getBoolean("allowed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.warn("Region", "Check failed", e)
        }
    }
}
