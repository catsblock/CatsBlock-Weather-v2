package com.catsblock.weather.data

import com.catsblock.weather.ui.developer.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAP_ZOOM = 5.5

object AgentLimits {
    const val MAX_CHARS = 70
    const val HISTORY_TURNS = 2
    const val HISTORY_CHARS = 280
}

object AgentMessages {
    const val TOO_LONG = "This message is either too long or too lengthy."
    const val FAILED = "Something went wrong while trying to send your message."
    const val REFUSED = "I can't help with this."
    const val LIMIT = "Your Agent usage limit has been reached."
    const val VIOLATION = "This message violates the terms of service."
    const val SPAM = "You sent too many messages in a short time. You are blocked for 30 minutes."
    const val REGION = "Our service is not available in your region."
    const val UNAVAILABLE = "The Agent service is unavailable. Try again later."
    const val BLOCKED = "This message was blocked because it does not look like a real question."
    const val BUSY = "The Agent is busy right now. Please try again in a moment."
    const val WORKERS_LIMIT = "Our services are currently unavailable because Cloudflare workers have reached their limit. Please check back at 0:00 UTC."

    val known: Set<String> = setOf(TOO_LONG, FAILED, REFUSED, LIMIT, REGION, SPAM, UNAVAILABLE, BLOCKED, BUSY, WORKERS_LIMIT)
}

enum class StepStatus { Run, Done, Fail }

data class AgentStep(
    val id: String,
    val status: StepStatus,
    val command: String
)

data class AgentTurn(val fromUser: Boolean, val text: String)

data class AgentDay(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val rainChance: Int,
    val precipMm: Double
)

enum class AgentMetric(val key: String, val title: String, val zeroBased: Boolean) {
    Humidity("humidity", "Humidity", false),
    Wind("wind", "Wind", true),
    Pressure("pressure", "Pressure", false),
    Clouds("clouds", "Cloud cover", true),
    Uv("uv", "UV index", true),
    Rain("rain", "Precipitation", true),
    Temperature("temperature", "Temperature", false);

    companion object {
        fun fromKey(key: String): AgentMetric? = entries.firstOrNull { it.key == key }
    }
}

sealed interface AgentCard {
    data class Current(
        val place: String,
        val temp: Double,
        val feels: Double,
        val humidity: Int,
        val windKmh: Double,
        val precipMm: Double,
        val code: Int,
        val isDay: Boolean
    ) : AgentCard

    data class Days(val history: Boolean, val place: String, val days: List<AgentDay>) : AgentCard

    data class Metric(
        val metric: AgentMetric,
        val place: String,
        val now: Double,
        val past: List<Double>,
        val ahead: List<Double>,
        val min: Double,
        val max: Double,
        val avg: Double
    ) : AgentCard

    data class MapView(
        val layer: String,
        val place: String,
        val lat: Double,
        val lon: Double,
        val zoom: Double,
        val centerLat: Double,
        val centerLon: Double
    ) : AgentCard
}

sealed interface AgentOutcome {
    data class Reply(val text: String, val cards: List<AgentCard>, val remaining: Int = -1, val resetSeconds: Long = 0L) : AgentOutcome
    data class Violation(val remaining: Int = -1, val resetSeconds: Long = 0L) : AgentOutcome
    data class Failure(val message: String, val retryAfterSeconds: Long = 0L) : AgentOutcome
}

class AgentClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun availability(): AgentAvailability = WorkerHealth.check()

    suspend fun ask(
        message: String,
        city: City,
        settings: AppSettings,
        history: List<AgentTurn>,
        onStep: (AgentStep) -> Unit = {}
    ): AgentOutcome = withContext(Dispatchers.IO) {
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext AgentOutcome.Failure(AgentMessages.UNAVAILABLE)
        try {
            val turns = JSONArray()
            history.takeLast(AgentLimits.HISTORY_TURNS).forEach { turn ->
                turns.put(
                    JSONObject()
                        .put("role", if (turn.fromUser) "user" else "assistant")
                        .put("text", turn.text.take(AgentLimits.HISTORY_CHARS))
                )
            }
            val payload = JSONObject()
                .put("message", message)
                .put("lat", city.lat)
                .put("lon", city.lon)
                .put("city", city.name)
                .put("lang", settings.language.code)
                .put("imperial", settings.fahrenheit)
                .put("history", turns)
                .toString()
            val builder = Request.Builder()
                .url("$base/v1/agent")
                .header("X-Agent-Stream", "1")
                .header("X-Device-Id", DeviceFingerprint.id)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            SecretKeys.appToken().takeIf { it.isNotEmpty() }?.let { builder.header("X-App-Token", it) }
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body
                val streaming = response.header("Content-Type").orEmpty().startsWith("application/x-ndjson")
                if (streaming && body != null) {
                    readStream(body.source(), onStep)
                } else {
                    val raw = body?.string().orEmpty()
                    if (WorkerHealth.isLimitResponse(response.code, response.header("Content-Type"), raw)) {
                        WorkerHealth.reportLimit()
                        AgentOutcome.Failure(AgentMessages.WORKERS_LIMIT)
                    } else {
                        outcomeOf(response.isSuccessful, runCatching { JSONObject(raw) }.getOrNull())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLog.warn("Agent", "Request failed", e)
            AgentOutcome.Failure(AgentMessages.FAILED)
        } catch (e: Exception) {
            AppLog.warn("Agent", "Unexpected failure", e)
            AgentOutcome.Failure(AgentMessages.FAILED)
        }
    }

    private fun readStream(source: BufferedSource, onStep: (AgentStep) -> Unit): AgentOutcome {
        var outcome: AgentOutcome = AgentOutcome.Failure(AgentMessages.FAILED)
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue
            val event = runCatching { JSONObject(line) }.getOrNull() ?: continue
            when (event.optString("type")) {
                "step" -> parseStep(event)?.let(onStep)
                "final" -> outcome = outcomeOf(event.optInt("status") in 200..299, event.optJSONObject("body"))
            }
        }
        return outcome
    }

    private fun parseStep(item: JSONObject): AgentStep? {
        val id = item.optString("id")
        if (id.isEmpty()) return null
        val status = when (item.optString("status")) {
            "run" -> StepStatus.Run
            "done" -> StepStatus.Done
            "fail" -> StepStatus.Fail
            else -> return null
        }
        return AgentStep(id, status, item.optString("command"))
    }

    private fun outcomeOf(success: Boolean, root: JSONObject?): AgentOutcome {
        if (root != null && root.optString("error") == "region_blocked") {
            RegionGuard.report()
            return AgentOutcome.Failure(AgentMessages.REGION)
        }
        if (success && root != null && root.optBoolean("violation")) {
            return AgentOutcome.Violation(root.optInt("remaining", -1), root.optLong("resetSeconds"))
        }
        if (!success || root == null) {
            val message = root?.optString("message").orEmpty()
            return AgentOutcome.Failure(
                if (message in AgentMessages.known) message else AgentMessages.FAILED,
                root?.optLong("retryAfterSeconds") ?: 0L
            )
        }
        val reply = root.optString("reply").trim()
        val cards = parseCards(root)
        return if (reply.isEmpty() && cards.isEmpty()) {
            AgentOutcome.Failure(AgentMessages.FAILED)
        } else {
            AgentOutcome.Reply(reply, cards, root.optInt("remaining", -1), root.optLong("resetSeconds"))
        }
    }

    private fun parseCards(root: JSONObject): List<AgentCard> {
        val array = root.optJSONArray("cards") ?: return emptyList()
        val out = ArrayList<AgentCard>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val card = runCatching { parseCard(item) }.getOrNull() ?: continue
            out.add(card)
        }
        return out
    }

    private fun parseCard(item: JSONObject): AgentCard? = when (item.optString("type")) {
        "current" -> AgentCard.Current(
            place = item.optString("place"),
            temp = item.getDouble("temp"),
            feels = item.getDouble("feels"),
            humidity = item.optInt("humidity"),
            windKmh = item.optDouble("wind"),
            precipMm = item.optDouble("precip"),
            code = item.optInt("code"),
            isDay = item.optBoolean("isDay", true)
        )
        "forecast", "history" -> {
            val days = item.getJSONArray("days")
            AgentCard.Days(
                history = item.optString("type") == "history",
                place = item.optString("place"),
                days = (0 until days.length()).map {
                    val d = days.getJSONObject(it)
                    AgentDay(
                        date = LocalDate.parse(d.getString("date")),
                        code = d.optInt("code"),
                        max = d.getDouble("max"),
                        min = d.getDouble("min"),
                        rainChance = d.optInt("pop"),
                        precipMm = d.optDouble("mm")
                    )
                }
            )
        }
        "metric" -> {
            val metric = AgentMetric.fromKey(item.optString("metric"))
            if (metric == null) {
                null
            } else {
                AgentCard.Metric(
                    metric = metric,
                    place = item.optString("place"),
                    now = item.getDouble("now"),
                    past = doubles(item.optJSONArray("past")),
                    ahead = doubles(item.optJSONArray("ahead")),
                    min = item.optDouble("min"),
                    max = item.optDouble("max"),
                    avg = item.optDouble("avg")
                )
            }
        }
        "map" -> AgentCard.MapView(
            layer = item.optString("layer", "none"),
            place = item.optString("place"),
            lat = item.getDouble("lat"),
            lon = item.getDouble("lon"),
            zoom = item.optDouble("zoom", DEFAULT_MAP_ZOOM),
            centerLat = item.optDouble("centerLat", item.getDouble("lat")),
            centerLon = item.optDouble("centerLon", item.getDouble("lon"))
        )
        else -> null
    }

    private fun doubles(array: JSONArray?): List<Double> =
        if (array == null) emptyList() else (0 until array.length()).map { array.optDouble(it) }
}
