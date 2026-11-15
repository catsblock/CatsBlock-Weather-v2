package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val publishedAt: Long,
    val imageUrl: String?,
    val url: String,
    val likes: Int
)

data class NewsPage(val articles: List<NewsArticle>, val cursor: String?)

sealed interface NewsLoad {
    data class Success(val page: NewsPage) : NewsLoad
    data class Failure(val status: Int) : NewsLoad
}

sealed interface NewsLikeResult {
    data class Liked(val likes: Int) : NewsLikeResult
    data class AlreadyLiked(val likes: Int) : NewsLikeResult
    data object LimitReached : NewsLikeResult
    data object RateLimited : NewsLikeResult
    data object Unavailable : NewsLikeResult
    data object Failed : NewsLikeResult
}

object NewsFeed {
    const val PAGE_SIZE = 15
    const val CACHE_MS = 15 * 60_000L
    const val MAX_LIKES = 10_000
    private const val JSON = "application/json; charset=utf-8"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private class Entry(val at: Long, val page: NewsPage)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > 48
    }

    fun available(): Boolean = SecretKeys.baseUrl().isNotEmpty() && SecretKeys.appToken().isNotEmpty()

    fun regionFor(language: String): String = when (language) {
        "pt-BR" -> "BR"
        "pt-PT" -> "PT"
        "es" -> "ES"
        "fr" -> "FR"
        "de" -> "DE"
        "it" -> "IT"
        "ru" -> "RU"
        "ko" -> "KR"
        "id" -> "ID"
        else -> "US"
    }

    private fun cacheKey(language: String, region: String, query: String, cursor: String): String =
        "$language|$region|$query|$cursor"

    private fun cached(key: String): NewsPage? {
        val entry = synchronized(cache) { cache[key] } ?: return null
        if (System.currentTimeMillis() - entry.at > CACHE_MS) {
            synchronized(cache) { cache.remove(key) }
            return null
        }
        return entry.page
    }

    suspend fun page(language: String, region: String, query: String, cursor: String): NewsLoad =
        withContext(Dispatchers.IO) {
            val key = cacheKey(language, region, query, cursor)
            cached(key)?.let { return@withContext NewsLoad.Success(it) }
            val base = SecretKeys.baseUrl()
            if (base.isEmpty()) return@withContext NewsLoad.Failure(0)
            val url = buildString {
                append(base)
                append("/v1/news?lang=")
                append(encode(language))
                append("&region=")
                append(encode(region))
                if (query.isNotBlank()) {
                    append("&q=")
                    append(encode(query))
                }
                if (cursor.isNotEmpty()) {
                    append("&cursor=")
                    append(encode(cursor))
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("X-App-Token", SecretKeys.appToken())
                .header("X-Device-Id", DeviceFingerprint.id)
                .header("X-Client-Platform", "android")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext NewsLoad.Failure(response.code)
                    val body = response.body?.string() ?: return@withContext NewsLoad.Failure(response.code)
                    val page = parsePage(JSONObject(body)) ?: return@withContext NewsLoad.Failure(response.code)
                    synchronized(cache) { cache[key] = Entry(System.currentTimeMillis(), page) }
                    NewsLoad.Success(page)
                }
            } catch (error: Exception) {
                NewsLoad.Failure(-1)
            }
        }

    suspend fun event(language: String, region: String, event: String): NewsLoad = withContext(Dispatchers.IO) {
        val normalized = event.trim().replace(Regex("\\s+"), " ").take(40)
        if (normalized.isBlank()) return@withContext NewsLoad.Success(NewsPage(emptyList(), null))
        val key = "event|$language|$region|$normalized"
        cached(key)?.let { return@withContext NewsLoad.Success(it) }
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext NewsLoad.Failure(0)
        val url = "$base/v1/news/event?lang=${encode(language)}&region=${encode(region)}&event=${encode(normalized)}"
        val request = Request.Builder().url(url)
            .header("X-App-Token", SecretKeys.appToken())
            .header("X-Device-Id", DeviceFingerprint.id)
            .header("X-Client-Platform", "android")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext NewsLoad.Failure(response.code)
                val page = parsePage(JSONObject(response.body?.string().orEmpty())) ?: return@withContext NewsLoad.Failure(response.code)
                synchronized(cache) { cache[key] = Entry(System.currentTimeMillis(), page) }
                NewsLoad.Success(page)
            }
        } catch (_: Exception) {
            NewsLoad.Failure(-1)
        }
    }
    sealed interface NewsReactionResult {
        data class Selected(val reaction: String) : NewsReactionResult
        data object RateLimited : NewsReactionResult
        data object Invalid : NewsReactionResult
        data object Unavailable : NewsReactionResult
        data object Failed : NewsReactionResult
    }

    val REACTIONS = listOf("🤩", "😁", "😭", "⚡", "👍", "👏")

    suspend fun react(articleId: String, reaction: String): NewsReactionResult = withContext(Dispatchers.IO) {
        if (reaction !in REACTIONS || articleId.isBlank()) return@withContext NewsReactionResult.Invalid
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext NewsReactionResult.Unavailable
        val payload = JSONObject().put("id", articleId).put("reaction", reaction).toString()
        val request = Request.Builder().url("$base/v1/news/reaction")
            .header("X-App-Token", SecretKeys.appToken())
            .header("X-Device-Id", DeviceFingerprint.id)
            .header("X-Client-Platform", "android")
            .post(payload.toRequestBody(JSON.toMediaType())).build()
        try {
            client.newCall(request).execute().use { response ->
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                when {
                    response.isSuccessful && json?.optString("reaction")?.let { it in REACTIONS } == true -> NewsReactionResult.Selected(json.optString("reaction"))
                    response.code == 429 -> NewsReactionResult.RateLimited
                    response.code == 400 -> NewsReactionResult.Invalid
                    response.code == 503 -> NewsReactionResult.Unavailable
                    else -> NewsReactionResult.Failed
                }
            }
        } catch (_: Exception) { NewsReactionResult.Failed }
    }

    suspend fun like(articleId: String): NewsLikeResult = withContext(Dispatchers.IO) {
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext NewsLikeResult.Unavailable
        val payload = JSONObject().put("id", articleId).toString()
        val request = Request.Builder()
            .url("$base/v1/news/like")
            .header("X-App-Token", SecretKeys.appToken())
            .header("X-Device-Id", DeviceFingerprint.id)
            .header("X-Client-Platform", "android")
            .post(payload.toRequestBody(JSON.toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                val error = json?.optString("error").orEmpty()
                val likes = json?.optInt("likes", -1) ?: -1
                when {
                    response.isSuccessful && likes >= 0 -> NewsLikeResult.Liked(likes)
                    error == "already_liked" -> NewsLikeResult.AlreadyLiked(likes.coerceAtLeast(0))
                    error == "limit_reached" -> NewsLikeResult.LimitReached
                    error == "rate_limited" -> NewsLikeResult.RateLimited
                    response.code == 503 -> NewsLikeResult.Unavailable
                    else -> NewsLikeResult.Failed
                }
            }
        } catch (error: Exception) {
            NewsLikeResult.Failed
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun parsePage(json: JSONObject): NewsPage? {
        val array = json.optJSONArray("articles") ?: return null
        val articles = ArrayList<NewsArticle>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            val url = item.optString("url")
            if (id.isEmpty() || title.isEmpty() || url.isEmpty()) continue
            if (!url.startsWith("https://") && !url.startsWith("http://")) continue
            articles.add(
                NewsArticle(
                    id = id,
                    title = title,
                    summary = item.optString("summary"),
                    source = item.optString("source"),
                    publishedAt = item.optLong("publishedAt", 0L),
                    imageUrl = item.optString("imageUrl").takeIf { it.startsWith("https://") || it.startsWith("http://") },
                    url = url,
                    likes = item.optInt("likes", 0).coerceIn(0, MAX_LIKES)
                )
            )
        }
        val cursor = json.optString("cursor").takeIf { it.isNotEmpty() }
        return NewsPage(articles, cursor)
    }
}
