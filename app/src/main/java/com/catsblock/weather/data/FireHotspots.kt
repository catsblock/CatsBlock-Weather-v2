package com.catsblock.weather.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.roundToLong

object FireHotspots {

    private const val CELL = 0.25
    private const val TTL = 3 * 3600_000L

    private val sources = listOf(
        "https://firms.modaps.eosdis.nasa.gov/data/active_fire/modis-c6.1/csv/MODIS_C6_1_Global_24h.csv",
        "https://firms.modaps.eosdis.nasa.gov/data/active_fire/suomi-npp-viirs-c2/csv/SUOMI_VIIRS_C2_Global_24h.csv"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: ByteArray? = null

    @Volatile
    private var cachedAt = 0L

    @Synchronized
    fun json(context: Context): ByteArray? {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < TTL }?.let { return it }
        val file = File(context.cacheDir, "fire_hotspots.json")
        if (file.exists() && now - file.lastModified() < TTL) {
            val stored = runCatching { file.readBytes() }.getOrNull()
            if (stored != null && stored.isNotEmpty()) {
                cached = stored
                cachedAt = file.lastModified()
                return stored
            }
        }
        for (source in sources) {
            val data = runCatching { download(source) }.getOrNull()
            if (data != null) {
                cached = data
                cachedAt = now
                runCatching { file.writeBytes(data) }
                return data
            }
        }
        return cached
    }

    private fun keep(confidence: String?): Boolean {
        if (confidence.isNullOrBlank()) return true
        val value = confidence.toIntOrNull()
        return if (value != null) value >= 30 else !confidence.equals("l", ignoreCase = true)
    }

    private fun download(url: String): ByteArray? {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val reader = response.body?.byteStream()?.bufferedReader() ?: return null
            val header = reader.readLine()?.split(',') ?: return null
            val iLat = header.indexOf("latitude")
            val iLon = header.indexOf("longitude")
            val iFrp = header.indexOf("frp")
            val iConf = header.indexOf("confidence")
            if (iLat < 0 || iLon < 0) return null
            val need = maxOf(iLat, iLon)
            val cells = HashMap<Long, DoubleArray>()
            var line = reader.readLine()
            while (line != null) {
                val p = line.split(',')
                if (p.size > need) {
                    val lat = p[iLat].toDoubleOrNull()
                    val lon = p[iLon].toDoubleOrNull()
                    if (lat != null && lon != null && keep(p.getOrNull(iConf))) {
                        val la = floor(lat / CELL).toInt()
                        val lo = floor(lon / CELL).toInt()
                        val key = (la.toLong() shl 32) xor (lo.toLong() and 0xffffffffL)
                        val frp = if (iFrp >= 0) p.getOrNull(iFrp)?.toDoubleOrNull() ?: 0.0 else 0.0
                        val cell = cells.getOrPut(key) { doubleArrayOf(la * CELL, lo * CELL, 0.0, 0.0) }
                        cell[2] += 1.0
                        if (frp > cell[3]) cell[3] = frp
                    }
                }
                line = reader.readLine()
            }
            if (cells.isEmpty()) return null
            val out = StringBuilder(cells.size * 28)
            out.append('[')
            var first = true
            for (cell in cells.values) {
                if (!first) out.append(',')
                first = false
                out.append('[').append(cell[0]).append(',').append(cell[1]).append(',')
                    .append(cell[2].toInt()).append(',').append((cell[3] * 10).roundToLong() / 10.0).append(']')
            }
            out.append(']')
            return out.toString().toByteArray(Charsets.UTF_8)
        }
    }
}
