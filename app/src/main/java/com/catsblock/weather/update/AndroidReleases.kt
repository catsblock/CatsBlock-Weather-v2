package com.catsblock.weather.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

internal object AndroidReleases {

    private const val REPO = "catsblock/CatsBlock-Weather-v2"
    private const val ASSET_SUFFIX = ".apk"
    private val digits = Regex("\\d+")

    fun latest(client: OkHttpClient, installedBuild: Long): UpdateInfo? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) error("http ${response.code}")
            val releases = JSONArray(response.body?.string().orEmpty())
            var best: UpdateInfo? = null
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                val build = digits.find(release.optString("tag_name"))?.value?.toIntOrNull() ?: continue
                if (build <= installedBuild || build <= (best?.build ?: 0)) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.getString("name").endsWith(ASSET_SUFFIX, ignoreCase = true)) {
                        best = UpdateInfo(
                            build,
                            release.optString("name").trim().ifEmpty { "Build $build" },
                            asset.getLong("size"),
                            asset.getString("browser_download_url")
                        )
                        break
                    }
                }
            }
            return best
        }
    }
}
