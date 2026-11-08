package com.catsblock.weather.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal sealed interface AdminResult<out T> {
    data class Success<T>(val value: T) : AdminResult<T>
    data class Rejected(val remaining: Int) : AdminResult<Nothing>
    data object Banned : AdminResult<Nothing>
    data object Busy : AdminResult<Nothing>
    data object Unavailable : AdminResult<Nothing>
    data object Expired : AdminResult<Nothing>
    data object Network : AdminResult<Nothing>
}

internal data class AdminPoint(val label: String, val value: Double)

internal data class AdminFeedback(
    val id: String,
    val timestamp: Long,
    val good: Boolean,
    val reasons: List<String>,
    val prompt: String,
    val reply: String
)

internal data class AdminFeedbackPage(val items: List<AdminFeedback>, val cursor: String)

internal data class AdminMetrics(
    val generatedAt: String,
    val activeNow: Int,
    val unique24h: Int,
    val usageToday: Long,
    val usageLimit: Long,
    val users: List<AdminPoint>,
    val requestsDaily: List<AdminPoint>,
    val errorsDaily: List<AdminPoint>,
    val hourly: Map<String, List<AdminPoint>>
)

internal class AdminClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun stageOne(code: String): AdminResult<String> =
        post("/v1/admin/stage1", JSONObject().put("code", code), null) { it.optString("token").ifEmpty { null } }

    suspend fun stageTwo(stageToken: String, answer: String): AdminResult<String> =
        post("/v1/admin/stage2", JSONObject().put("token", stageToken).put("answer", answer), null) {
            it.optString("token").ifEmpty { null }
        }

    suspend fun metrics(session: String, days: Int): AdminResult<AdminMetrics> =
        post("/v1/admin/metrics", JSONObject().put("days", days), session, ::parseMetrics)

    suspend fun feedback(session: String, cursor: String): AdminResult<AdminFeedbackPage> =
        post("/v1/admin/feedback", JSONObject().put("cursor", cursor), session, ::parseFeedback)

    private suspend fun <T> post(
        path: String,
        body: JSONObject,
        bearer: String?,
        parse: (JSONObject) -> T?
    ): AdminResult<T> = withContext(Dispatchers.IO) {
        val base = SecretKeys.baseUrl()
        val token = SecretKeys.appToken()
        if (base.isEmpty() || token.isEmpty()) return@withContext AdminResult.Unavailable
        try {
            val builder = Request.Builder()
                .url(base + path)
                .header("X-App-Token", token)
                .header("X-Device-Id", DeviceFingerprint.id)
                .header("X-Client-Platform", "android")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (bearer != null) builder.header("Authorization", "Bearer $bearer")
            client.newCall(builder.build()).execute().use { response ->
                val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                when (response.code) {
                    200 -> root?.let(parse)?.let { AdminResult.Success(it) } ?: AdminResult.Network
                    401 -> if (root != null && root.optString("error") == "invalid") {
                        AdminResult.Rejected(root.optInt("remaining", 0))
                    } else {
                        AdminResult.Expired
                    }
                    403 -> AdminResult.Banned
                    429 -> AdminResult.Busy
                    404, 503 -> AdminResult.Unavailable
                    else -> AdminResult.Network
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AdminResult.Network
        } catch (e: Exception) {
            AdminResult.Network
        }
    }

    private fun parseMetrics(root: JSONObject): AdminMetrics? {
        val usage = root.optJSONObject("usage") ?: return null
        val active = root.optJSONObject("active") ?: return null
        val users = root.optJSONObject("users") ?: return null
        val requests = root.optJSONObject("requests") ?: return null
        val daily = requests.optJSONArray("daily") ?: return null
        val hourly = requests.optJSONArray("hourly") ?: return null
        return AdminMetrics(
            generatedAt = root.optString("generatedAt"),
            activeNow = active.optInt("now"),
            unique24h = root.optInt("unique24h"),
            usageToday = usage.optLong("today"),
            usageLimit = usage.optLong("limit", 100_000L),
            users = points(users.optJSONArray("series"), "day", "value"),
            requestsDaily = points(daily, "day", "total"),
            errorsDaily = points(daily, "day", "errors"),
            hourly = HOURLY_KEYS.associateWith { points(hourly, "hour", it) }
        )
    }

    private fun parseFeedback(root: JSONObject): AdminFeedbackPage? {
        val array = root.optJSONArray("items") ?: return null
        val items = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            val reasons = item.optJSONArray("reasons")
            AdminFeedback(
                id = id,
                timestamp = item.optLong("ts"),
                good = item.optString("rating") == "good",
                reasons = if (reasons == null) emptyList() else (0 until reasons.length()).map { reasons.optString(it) },
                prompt = item.optString("prompt"),
                reply = item.optString("reply")
            )
        }
        return AdminFeedbackPage(items, root.optString("cursor"))
    }

    private fun points(array: JSONArray?, labelKey: String, valueKey: String): List<AdminPoint> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { AdminPoint(it.optString(labelKey), it.optDouble(valueKey, 0.0)) }
        }
    }

    private companion object {
        val HOURLY_KEYS = listOf("total", "weather", "visual", "openweather", "agent")
    }
}
