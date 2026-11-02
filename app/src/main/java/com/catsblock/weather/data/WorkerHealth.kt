package com.catsblock.weather.data

import com.catsblock.weather.ui.developer.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

enum class AgentAvailability { Available, Unavailable, WorkersLimit }

object WorkerHealth {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _limitReached = MutableStateFlow(false)
    val limitReached: StateFlow<Boolean> = _limitReached.asStateFlow()

    @Volatile
    private var limitedOn: LocalDate? = null

    fun reportLimit() {
        limitedOn = LocalDate.now(ZoneOffset.UTC)
        _limitReached.value = true
    }

    fun reportHealthy() {
        limitedOn = null
        _limitReached.value = false
    }

    private fun expireStale() {
        val day = limitedOn ?: return
        if (day != LocalDate.now(ZoneOffset.UTC)) reportHealthy()
    }

    fun isLimitResponse(code: Int, contentType: String?, body: String): Boolean {
        if (code < 400) return false
        if (body.contains("1027")) return true
        if (code != 429) return false
        val json = contentType.orEmpty().contains("json", ignoreCase = true) || body.trimStart().startsWith("{")
        return !json
    }

    suspend fun check(): AgentAvailability = withContext(Dispatchers.IO) {
        expireStale()
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext AgentAvailability.Unavailable
        try {
            val builder = Request.Builder()
                .url("$base/v1/agent/status")
                .header("X-Device-Id", DeviceFingerprint.id)
                .get()
            SecretKeys.appToken().takeIf { it.isNotEmpty() }?.let { builder.header("X-App-Token", it) }
            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (isLimitResponse(response.code, response.header("Content-Type"), raw)) {
                    reportLimit()
                    AgentAvailability.WorkersLimit
                } else {
                    reportHealthy()
                    val available = response.isSuccessful &&
                        runCatching { JSONObject(raw).optBoolean("available", false) }.getOrDefault(false)
                    if (available) AgentAvailability.Available else AgentAvailability.Unavailable
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.warn("Agent", "Availability check failed", e)
            AgentAvailability.Unavailable
        }
    }
}
