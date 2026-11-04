package com.catsblock.weather.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.roundToLong

object FireHotspots {

    private const val CELL = 0.25
    private const val TTL = 3 * 3600_000L
    private const val LIVE = Long.MIN_VALUE
    private const val WEEK = "https://firms.modaps.eosdis.nasa.gov/data/active_fire/modis-c6.1/csv/MODIS_C6_1_Global_7d.csv"

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

    fun fetchedAt(): Long = cachedAt

    @Synchronized
    fun json(context: Context, day: Long? = null): ByteArray? =
        if (day == null) live(context) else historic(context, day)

    private fun historic(context: Context, day: Long): ByteArray? {
        val now = System.currentTimeMillis()
        val file = File(context.cacheDir, "fire_day_$day.json")
        if (file.exists() && now - file.lastModified() < TTL) {
            val stored = runCatching { file.readBytes() }.getOrNull()
            if (stored != null && stored.isNotEmpty()) return stored
        }
        val days = runCatching { download(WEEK, true) }.getOrNull()
        if (days == null) return if (file.exists()) runCatching { file.readBytes() }.getOrNull() else null
        days.forEach { (key, data) -> runCatching { File(context.cacheDir, "fire_day_$key.json").writeBytes(data) } }
        return days[day]
    }

    private fun live(context: Context): ByteArray? {
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
            val data = runCatching { download(source, false) }.getOrNull()?.get(LIVE)
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

    private fun confidenceOf(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        raw.toDoubleOrNull()?.let { return it }
        return when (raw.trim().lowercase()) {
            "l" -> 20.0
            "n" -> 60.0
            "h" -> 90.0
            else -> 0.0
        }
    }

    private fun dayOf(date: String?, days: HashMap<String, Long>): Long {
        if (date.isNullOrBlank()) return -1L
        return days.getOrPut(date) { runCatching { LocalDate.parse(date).toEpochDay() }.getOrDefault(-1L) }
    }

    private fun minuteOf(date: String?, time: String?, days: HashMap<String, Long>): Double {
        if (time.isNullOrBlank()) return 0.0
        val day = dayOf(date, days)
        if (day < 0) return 0.0
        val hhmm = time.trim().toIntOrNull() ?: return 0.0
        return (day * 1440 + (hhmm / 100) * 60 + hhmm % 100).toDouble()
    }

    private fun download(url: String, split: Boolean): Map<Long, ByteArray>? {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val reader = response.body?.byteStream()?.bufferedReader() ?: return null
            val header = reader.readLine()?.split(',') ?: return null
            val iLat = header.indexOf("latitude")
            val iLon = header.indexOf("longitude")
            val iFrp = header.indexOf("frp")
            val iConf = header.indexOf("confidence")
            val iDate = header.indexOf("acq_date")
            val iTime = header.indexOf("acq_time")
            val iBright = maxOf(header.indexOf("brightness"), header.indexOf("bright_ti4"))
            val iDn = header.indexOf("daynight")
            val days = HashMap<String, Long>()
            if (iLat < 0 || iLon < 0) return null
            val need = maxOf(iLat, iLon)
            val buckets = HashMap<Long, HashMap<Long, DoubleArray>>()
            var line = reader.readLine()
            while (line != null) {
                val p = line.split(',')
                if (p.size > need) {
                    val lat = p[iLat].toDoubleOrNull()
                    val lon = p[iLon].toDoubleOrNull()
                    val bucket = if (split) dayOf(p.getOrNull(iDate), days) else LIVE
                    if (lat != null && lon != null && bucket != -1L && keep(p.getOrNull(iConf))) {
                        val cells = buckets.getOrPut(bucket) { HashMap() }
                        val la = floor(lat / CELL).toInt()
                        val lo = floor(lon / CELL).toInt()
                        val key = (la.toLong() shl 32) xor (lo.toLong() and 0xffffffffL)
                        val frp = if (iFrp >= 0) p.getOrNull(iFrp)?.toDoubleOrNull() ?: 0.0 else 0.0
                        val cell = cells.getOrPut(key) { doubleArrayOf(la * CELL, lo * CELL, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
                        cell[2] += 1.0
                        if (frp > cell[3]) cell[3] = frp
                        val minute = minuteOf(p.getOrNull(iDate), p.getOrNull(iTime), days)
                        if (minute > 0.0) {
                            if (cell[4] == 0.0 || minute < cell[4]) cell[4] = minute
                            if (minute > cell[5]) cell[5] = minute
                        }
                        val bright = if (iBright >= 0) p.getOrNull(iBright)?.toDoubleOrNull() ?: 0.0 else 0.0
                        if (bright > cell[6]) cell[6] = bright
                        val conf = confidenceOf(p.getOrNull(iConf))
                        if (conf > cell[7]) cell[7] = conf
                        if (iDn >= 0 && p.getOrNull(iDn)?.trim() == "N") cell[8] += 1.0
                    }
                }
                line = reader.readLine()
            }
            if (buckets.isEmpty()) return null
            return buckets.mapValues { encode(it.value.values) }
        }
    }

    private fun encode(cells: Collection<DoubleArray>): ByteArray {
        val out = StringBuilder(cells.size * 56)
        out.append('[')
        var first = true
        for (cell in cells) {
            if (!first) out.append(',')
            first = false
            out.append('[').append(cell[0]).append(',').append(cell[1]).append(',')
                .append(cell[2].toInt()).append(',').append((cell[3] * 10).roundToLong() / 10.0).append(',')
                .append(cell[4].toLong()).append(',').append(cell[5].toLong()).append(',')
                .append(cell[6].roundToLong()).append(',').append(cell[7].roundToLong()).append(',')
                .append(cell[8].toInt()).append(']')
        }
        out.append(']')
        return out.toString().toByteArray(Charsets.UTF_8)
    }
}
