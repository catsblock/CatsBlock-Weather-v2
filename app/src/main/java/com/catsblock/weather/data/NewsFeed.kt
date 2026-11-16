package com.catsblock.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val publishedAt: Long,
    val imageUrl: String?,
    val url: String
)

data class NewsPage(val articles: List<NewsArticle>, val cursor: String?)

sealed interface NewsLoad {
    data class Success(val page: NewsPage) : NewsLoad
    data class Failure(val status: Int) : NewsLoad
}

object NewsFeed {
    const val PAGE_SIZE = 15
    const val CACHE_MS = 15 * 60_000L
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

    private fun cacheKey(language: String, region: String, query: String, cursor: String) = "$language|$region|$query|$cursor"

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

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("X-App-Token", SecretKeys.appToken())
        .header("X-Device-Id", DeviceFingerprint.id)
        .header("X-Client-Platform", "android")
        .build()

    suspend fun page(language: String, region: String, query: String, cursor: String): NewsLoad = withContext(Dispatchers.IO) {
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
            client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) return@withContext NewsLoad.Failure(response.code)
                val body = response.body?.string() ?: return@withContext NewsLoad.Failure(response.code)
                val page = parsePage(JSONObject(body)) ?: return@withContext NewsLoad.Failure(response.code)
                store(key, page)
                NewsLoad.Success(page)
            }
        } catch (_: Exception) {
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
            client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) return@withContext NewsLoad.Failure(response.code)
                val page = parsePage(JSONObject(response.body?.string().orEmpty()))
                    ?: return@withContext NewsLoad.Failure(response.code)
                store(key, page)
                NewsLoad.Success(page)
            }
        } catch (_: Exception) {
            NewsLoad.Failure(-1)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun parsePage(json: JSONObject): NewsPage? {
        val array = json.optJSONArray("articles") ?: return null
        val articles = ArrayList<NewsArticle>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            val url = item.optString("url")
            if (id.isEmpty() || title.isEmpty() || url.isEmpty()) continue
            if (!url.startsWith("https://", ignoreCase = true)) continue
            articles.add(
                NewsArticle(
                    id = id,
                    title = title,
                    summary = item.optString("summary"),
                    source = item.optString("source"),
                    publishedAt = item.optLong("publishedAt", 0L),
                    imageUrl = item.optString("imageUrl").takeIf { it.startsWith("https://", ignoreCase = true) },
                    url = url
                )
            )
        }
        val cursor = json.optString("cursor").takeIf(String::isNotEmpty)
        return NewsPage(articles, cursor)
    }
}
