package com.catsblock.weather.media

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object MediaCache {
    private val client = OkHttpClient()

    suspend fun fetch(context: Context, urls: List<String>): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "media").apply { mkdirs() }
        for (u in urls) {
            val name = u.hashCode().toUInt().toString(16) + "." + u.substringAfterLast('.').take(4)
            val file = File(dir, name)
            if (file.exists() && file.length() > 10_000) return@withContext file
            val tmp = File(dir, "$name.part")
            try {
                client.newCall(Request.Builder().url(u).build()).execute().use { r ->
                    if (r.isSuccessful) {
                        r.body!!.byteStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    }
                }
                if (tmp.length() > 10_000 && tmp.renameTo(file)) return@withContext file
                tmp.delete()
            } catch (e: CancellationException) {
                tmp.delete()
                throw e
            } catch (_: Exception) {
                tmp.delete()
            }
        }
        null
    }
}
