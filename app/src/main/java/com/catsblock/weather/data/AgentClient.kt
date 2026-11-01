package com.catsblock.weather.data

import com.catsblock.weather.ui.developer.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object AgentLimits {
    const val MAX_CHARS = 70
}

object AgentMessages {
    const val TOO_LONG = "This message is either too long or too lengthy."
    const val FAILED = "Something went wrong while trying to send your message."
    const val REFUSED = "I can't help with this."
    const val LIMIT = "You have reached the limit of 5 messages. Please wait 24 hours before trying again."

    val known: Set<String> = setOf(TOO_LONG, FAILED, REFUSED, LIMIT)
}

data class AgentDay(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val rainChance: Int,
    val precipMm: Double
)

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
}

sealed interface AgentOutcome {
    data class Reply(val text: String, val cards: List<AgentCard>) : AgentOutcome
    data class Failure(val message: String) : AgentOutcome
}

class AgentClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun ask(message: String, city: City, settings: AppSettings): AgentOutcome = withContext(Dispatchers.IO) {
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext AgentOutcome.Failure(AgentMessages.FAILED)
        try {
            val payload = JSONObject()
                .put("message", message)
                .put("lat", city.lat)
                .put("lon", city.lon)
                .put("city", city.name)
                .put("lang", settings.language.code)
                .put("imperial", settings.fahrenheit)
                .toString()
            val builder = Request.Builder()
                .url("$base/v1/agent")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            SecretKeys.appToken().takeIf { it.isNotEmpty() }?.let { builder.header("X-App-Token", it) }
            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val root = runCatching { JSONObject(raw) }.getOrNull()
                if (!response.isSuccessful || root == null) {
                    val known = root?.optString("message").orEmpty()
                    return@use AgentOutcome.Failure(if (known in AgentMessages.known) known else AgentMessages.FAILED)
                }
                val reply = root.optString("reply").trim()
                val cards = parseCards(root)
                if (reply.isEmpty() && cards.isEmpty()) AgentOutcome.Failure(AgentMessages.FAILED) else AgentOutcome.Reply(reply, cards)
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
        else -> null
    }
}
