package com.catsblock.weather.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Properties

private const val DEFAULT_MAP_ZOOM = 5.5
private const val MAX_BODY_BYTES = 1_000_000

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

enum class AgentAvailability { Available, Unavailable, WorkersLimit }

enum class StepStatus { Run, Done, Fail }

data class AgentStep(val id: String, val status: StepStatus, val command: String)

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

    data class Series(
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

object WorkerConfig {
    private val props: Properties by lazy {
        Properties().apply {
            runCatching { WorkerConfig::class.java.getResourceAsStream("/build.properties")?.use { load(it) } }
        }
    }

    val baseUrl: String
        get() = props.getProperty("cloudflare").orEmpty().trim().trimEnd('/').takeIf { it.startsWith("https://") }.orEmpty()

    val appToken: String
        get() = props.getProperty("token").orEmpty().trim()
}

object DeviceFingerprint {

    private const val VERSION = "catsblock-desktop-device-v1"

    val id: String by lazy { compute() }

    private fun compute(): String {
        val screen = runCatching {
            if (java.awt.GraphicsEnvironment.isHeadless()) "" else {
                val size = java.awt.Toolkit.getDefaultToolkit().screenSize
                "${minOf(size.width, size.height)}x${maxOf(size.width, size.height)}"
            }
        }.getOrDefault("")
        val traits = listOf(
            VERSION,
            System.getProperty("os.name").orEmpty(),
            System.getProperty("os.arch").orEmpty(),
            Runtime.getRuntime().availableProcessors().toString(),
            System.getenv("COMPUTERNAME").orEmpty(),
            System.getenv("USERNAME").orEmpty(),
            System.getenv("PROCESSOR_IDENTIFIER").orEmpty(),
            screen
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(traits.joinToString("|").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object AgentBlockStore {

    private val file = File(AppPaths.dir, "agent.properties")

    fun load(): Long {
        if (!file.exists()) return 0L
        val until = runCatching {
            Properties().apply { file.inputStream().use { load(it) } }.getProperty("blockedUntil")?.toLongOrNull()
        }.getOrNull() ?: 0L
        return if (until > System.currentTimeMillis()) until else 0L
    }

    fun save(until: Long) {
        runCatching {
            file.parentFile.mkdirs()
            val temp = File(file.parentFile, "agent.properties.tmp")
            temp.outputStream().use { Properties().apply { this["blockedUntil"] = until.toString() }.store(it, null) }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

object WorkerHealth {

    @Volatile
    private var limitedOn: LocalDate? = null

    fun reportLimit() {
        limitedOn = LocalDate.now(ZoneOffset.UTC)
    }

    fun reportHealthy() {
        limitedOn = null
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

    suspend fun check(http: HttpClient): AgentAvailability = withContext(Dispatchers.IO) {
        expireStale()
        val base = WorkerConfig.baseUrl
        if (base.isEmpty()) return@withContext AgentAvailability.Unavailable
        try {
            val builder = HttpRequest.newBuilder(URI.create("$base/v1/agent/status"))
                .timeout(Duration.ofSeconds(20))
                .header("X-Device-Id", DeviceFingerprint.id)
                .GET()
            WorkerConfig.appToken.takeIf { it.isNotEmpty() }?.let { builder.header("X-App-Token", it) }
            val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            val raw = response.body().use { String(it.readNBytes(MAX_BODY_BYTES), Charsets.UTF_8) }
            val type = response.headers().firstValue("Content-Type").orElse("")
            if (isLimitResponse(response.statusCode(), type, raw)) {
                reportLimit()
                AgentAvailability.WorkersLimit
            } else {
                reportHealthy()
                val available = response.statusCode() in 200..299 &&
                    runCatching { JSONObject(raw).optBoolean("available", false) }.getOrDefault(false)
                if (available) AgentAvailability.Available else AgentAvailability.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AgentAvailability.Unavailable
        }
    }
}

class AgentClient {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    suspend fun availability(): AgentAvailability = WorkerHealth.check(http)

    suspend fun ask(
        message: String,
        city: SavedCity,
        settings: DesktopSettings,
        history: List<AgentTurn>,
        onStep: (AgentStep) -> Unit = {}
    ): AgentOutcome = withContext(Dispatchers.IO) {
        val base = WorkerConfig.baseUrl
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
                .put("lang", settings.language)
                .put("imperial", settings.fahrenheit)
                .put("history", turns)
                .toString()
            val builder = HttpRequest.newBuilder(URI.create("$base/v1/agent"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Agent-Stream", "1")
                .header("X-Device-Id", DeviceFingerprint.id)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
            WorkerConfig.appToken.takeIf { it.isNotEmpty() }?.let { builder.header("X-App-Token", it) }
            val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            val type = response.headers().firstValue("Content-Type").orElse("")
            val code = response.statusCode()
            response.body().use { stream ->
                if (type.startsWith("application/x-ndjson")) {
                    readStream(stream.bufferedReader(Charsets.UTF_8), onStep)
                } else {
                    val raw = String(stream.readNBytes(MAX_BODY_BYTES), Charsets.UTF_8)
                    if (WorkerHealth.isLimitResponse(code, type, raw)) {
                        WorkerHealth.reportLimit()
                        AgentOutcome.Failure(AgentMessages.WORKERS_LIMIT)
                    } else {
                        outcomeOf(code in 200..299, runCatching { JSONObject(raw) }.getOrNull())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AgentOutcome.Failure(AgentMessages.FAILED)
        }
    }

    private fun readStream(reader: BufferedReader, onStep: (AgentStep) -> Unit): AgentOutcome {
        var outcome: AgentOutcome = AgentOutcome.Failure(AgentMessages.FAILED)
        while (true) {
            val line = reader.readLine() ?: break
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
            if (metric == null) null else AgentCard.Series(
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
