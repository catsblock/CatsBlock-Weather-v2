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
    val likes: Int,
    val liked: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null
) {
    fun countOf(reaction: Reaction): Int = reactions[reaction.emoji] ?: 0

    val visibleReactions: List<Reaction>
        get() = Reaction.entries.filter { countOf(it) > 0 || myReaction == it.emoji }
}

fun NewsArticle.withLike(liked: Boolean): NewsArticle {
    if (this.liked == liked) return this
    val count = (likes + if (liked) 1 else -1).coerceIn(0, NewsFeed.MAX_LIKES)
    return copy(liked = liked, likes = count)
}

fun NewsArticle.withReaction(emoji: String?): NewsArticle {
    if (myReaction == emoji) return this
    val counts = reactions.toMutableMap()
    myReaction?.let { active -> counts[active] = ((counts[active] ?: 0) - 1).coerceAtLeast(0) }
    emoji?.let { target -> counts[target] = ((counts[target] ?: 0) + 1).coerceAtMost(NewsFeed.MAX_LIKES) }
    return copy(reactions = counts, myReaction = emoji)
}

data class NewsPage(val articles: List<NewsArticle>, val cursor: String?)

sealed interface NewsLoad {
    data class Success(val page: NewsPage) : NewsLoad
    data class Failure(val status: Int) : NewsLoad
}

sealed interface NewsLikeResult {
    data class Updated(val likes: Int, val liked: Boolean) : NewsLikeResult
    data object LimitReached : NewsLikeResult
    data class RateLimited(val retryAfterSeconds: Long) : NewsLikeResult
    data object Unavailable : NewsLikeResult
    data object Failed : NewsLikeResult
    data object AuthenticationRequired : NewsLikeResult
}

sealed interface NewsReactionResult {
    data class Updated(val reaction: String?, val counts: Map<String, Int>) : NewsReactionResult
    data class RateLimited(val retryAfterSeconds: Long) : NewsReactionResult
    data object Unavailable : NewsReactionResult
    data object Failed : NewsReactionResult
    data object AuthenticationRequired : NewsReactionResult
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

    private fun store(key: String, page: NewsPage) {
        synchronized(cache) { cache[key] = Entry(System.currentTimeMillis(), page) }
    }

    private fun request(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("X-App-Token", SecretKeys.appToken())
        .header("X-Device-Id", DeviceFingerprint.id)
        .header("X-Client-Platform", "android")
        .apply { AccountAuth.accessToken().takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }

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
            try {
                client.newCall(request(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext NewsLoad.Failure(response.code)
                    val body = response.body?.string() ?: return@withContext NewsLoad.Failure(response.code)
                    val page = parsePage(JSONObject(body)) ?: return@withContext NewsLoad.Failure(response.code)
                    store(key, page)
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
        try {
            client.newCall(request(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext NewsLoad.Failure(response.code)
                val page = parsePage(JSONObject(response.body?.string().orEmpty())) ?: return@withContext NewsLoad.Failure(response.code)
                store(key, page)
                NewsLoad.Success(page)
            }
        } catch (_: Exception) {
            NewsLoad.Failure(-1)
        }
    }

    suspend fun setReaction(articleId: String, reaction: String?): NewsReactionResult = withContext(Dispatchers.IO) {
        if (articleId.isBlank()) return@withContext NewsReactionResult.Failed
        if (reaction != null && Reaction.of(reaction) == null) return@withContext NewsReactionResult.Failed
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext NewsReactionResult.Unavailable
        val payload = JSONObject().put("id", articleId).put("reaction", reaction.orEmpty()).toString()
        val call = request("$base/v1/news/reaction")
            .post(payload.toRequestBody(JSON.toMediaType()))
            .build()
        try {
            client.newCall(call).execute().use { response ->
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                when {
                    response.code == 401 -> NewsReactionResult.AuthenticationRequired
                    response.isSuccessful && json != null -> {
                        val selected = json.optString("reaction").takeIf { Reaction.of(it) != null }
                        NewsReactionResult.Updated(selected, parseCounts(json.optJSONObject("reactions")))
                    }
                    response.code == 429 -> NewsReactionResult.RateLimited(response.header("Retry-After")?.toLongOrNull()?.coerceAtLeast(1) ?: 900L)
                    response.code == 503 -> NewsReactionResult.Unavailable
                    else -> NewsReactionResult.Failed
                }
            }
        } catch (_: Exception) {
            NewsReactionResult.Failed
        }
    }

    suspend fun setLike(articleId: String, liked: Boolean): NewsLikeResult = withContext(Dispatchers.IO) {
        if (articleId.isBlank()) return@withContext NewsLikeResult.Failed
        val base = SecretKeys.baseUrl()
        if (base.isEmpty()) return@withContext NewsLikeResult.Unavailable
        val payload = JSONObject().put("id", articleId).put("liked", liked)
        val call = request("$base/v1/news/like")
            .post(payload.toString().toRequestBody(JSON.toMediaType()))
            .build()
        try {
            client.newCall(call).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                val error = json?.optString("error").orEmpty()
                val likes = json?.optInt("likes", -1) ?: -1
                when {
                    response.code == 401 -> NewsLikeResult.AuthenticationRequired
                    response.isSuccessful && likes >= 0 -> NewsLikeResult.Updated(
                        likes.coerceIn(0, MAX_LIKES),
                        json?.optBoolean("liked", liked) ?: liked
                    )
                    error == "limit_reached" -> NewsLikeResult.LimitReached
                    error == "rate_limited" -> NewsLikeResult.RateLimited(response.header("Retry-After")?.toLongOrNull()?.coerceAtLeast(1) ?: 900L)
                    response.code == 503 -> NewsLikeResult.Unavailable
                    else -> NewsLikeResult.Failed
                }
            }
        } catch (_: Exception) {
            NewsLikeResult.Failed
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseCounts(json: JSONObject?): Map<String, Int> {
        if (json == null) return emptyMap()
        return Reaction.entries.associate { entry ->
            entry.emoji to json.optInt(entry.emoji, 0).coerceIn(0, MAX_LIKES)
        }
    }

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
                    likes = item.optInt("likes", 0).coerceIn(0, MAX_LIKES),
                    liked = item.optBoolean("liked", false),
                    reactions = parseCounts(item.optJSONObject("reactions")),
                    myReaction = item.optString("myReaction").takeIf { Reaction.of(it) != null }
                )
            )
        }
        val cursor = json.optString("cursor").takeIf { it.isNotEmpty() }
        return NewsPage(articles, cursor)
    }
}
