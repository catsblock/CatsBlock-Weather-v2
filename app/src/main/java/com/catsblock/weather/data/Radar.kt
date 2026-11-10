package com.catsblock.weather.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

enum class StormIntensity { Light, Moderate, Severe, Extreme }

data class StormTrack(
    val bearingDeg: Double?,
    val speedKmh: Double?,
    val edgeDistanceKm: Double,
    val coreDistanceKm: Double,
    val peakDbz: Double,
    val rateMmh: Double,
    val intensity: StormIntensity,
    val etaMinutes: Int?,
    val durationMinutes: Int?,
    val onTarget: Boolean
)

data class RadarReading(
    val wet: Boolean,
    val strength: Double,
    val track: StormTrack? = null,
    val cityDbz: Double = 0.0,
    val anyEcho: Boolean = true,
    val frameEpoch: Long = 0L
)

fun reflectivityToRate(dbz: Double): Double =
    (10.0.pow(dbz / 10.0) / 200.0).pow(0.625)

fun stormIntensityOf(rateMmh: Double): StormIntensity = when {
    rateMmh < 2.5 -> StormIntensity.Light
    rateMmh < 10.0 -> StormIntensity.Moderate
    rateMmh < 50.0 -> StormIntensity.Severe
    else -> StormIntensity.Extreme
}

data class RadarFrameRef(val time: Long, val path: String)

class RadarFrameSet(val host: String, val frames: List<RadarFrameRef>)

object RadarFrames {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: RadarFrameSet? = null

    @Volatile
    private var cachedAt = 0L

    suspend fun set(): RadarFrameSet? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < 300_000L }?.let { return@withContext it }
        val fresh = runCatching {
            client.newCall(Request.Builder().url("https://api.rainviewer.com/public/weather-maps.json").build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = JSONObject(response.body?.string() ?: return@use null)
                    val host = json.getString("host")
                    val past = json.getJSONObject("radar").getJSONArray("past")
                    if (past.length() == 0) return@use null
                    val frames = (0 until past.length()).map {
                        val item = past.getJSONObject(it)
                        RadarFrameRef(item.getLong("time"), item.getString("path"))
                    }.sortedBy { it.time }
                    RadarFrameSet(host, frames)
                }
        }.getOrNull()
        if (fresh != null) {
            cached = fresh
            cachedAt = now
        }
        fresh ?: cached
    }

    suspend fun base(): String? = set()?.let { it.host + it.frames.last().path }

    fun tileUrl(base: String, zoom: Int, x: Int, y: Int): String = "$base/256/$zoom/$x/$y/2/1_1.png"

    fun reflectivityTileUrl(host: String, path: String, zoom: Int, x: Int, y: Int): String =
        "$host$path/256/$zoom/$x/$y/2/0_0.png"

    internal fun http(): OkHttpClient = client
}

object RadarProbe {

    private const val ZOOM = 7
    private const val TILE = 256
    private const val RADIUS = 1
    private const val SIDE = TILE * (2 * RADIUS + 1)
    private const val NONE = -99f
    private const val ECHO_DBZ = 15f
    private const val TRACK_DBZ = 20f
    private const val MIN_AREA = 8
    private const val WINDOW = 3
    private const val STALE_SECONDS = 45L * 60L
    private const val MAX_SPEED_KMH = 140.0
    private const val MIN_SPEED_KMH = 3.0
    private const val HORIZON_MINUTES = 360.0
    private const val MAX_RANGE_KM = 320.0
    private const val MAX_PALETTE_DISTANCE = 900

    private val palette = intArrayOf(
        0x88ddee, 0x6cd1eb, 0x51c5e8, 0x36bae5, 0x1baee2, 0x00a3e0, 0x009ad5, 0x0091ca, 0x0088bf, 0x007fb4,
        0x0077aa, 0x0070a3, 0x00699c, 0x006295, 0x005b8e, 0x005588, 0x005180, 0x004e78, 0x004a70, 0x004768,
        0xffee00, 0xffe000, 0xffd200, 0xffc500, 0xffb700, 0xffaa00, 0xff9f00, 0xff9500, 0xff8b00, 0xff8100,
        0xff4400, 0xf23600, 0xe62800, 0xd91b00, 0xcd0d00, 0xc10000, 0xa80000, 0x8f0000, 0x760000, 0x5d0000,
        0xffaaff, 0xff9fff, 0xff95ff, 0xff8bff, 0xff81ff, 0xff77ff, 0xff6cff, 0xff62ff, 0xff58ff, 0xff4eff,
        0xffffff
    )

    private class Geometry(
        val originX: Int,
        val originY: Int,
        val cityX: Double,
        val cityY: Double,
        val kmPerPx: Double
    )

    private class Cell(
        val area: Int,
        val x: Double,
        val y: Double,
        val peak: Float,
        val edgePx: Double
    )

    private class Cached(val key: String, val reading: RadarReading)

    @Volatile
    private var last: Cached? = null

    suspend fun read(lat: Double, lon: Double): RadarReading? = withContext(Dispatchers.IO) {
        val set = RadarFrames.set() ?: return@withContext null
        val latest = set.frames.lastOrNull() ?: return@withContext null
        val nowEpoch = System.currentTimeMillis() / 1000L
        val age = nowEpoch - latest.time
        if (age > STALE_SECONDS) return@withContext null
        val key = "${latest.time}|${"%.2f".format(java.util.Locale.US, lat)}|${"%.2f".format(java.util.Locale.US, lon)}"
        last?.takeIf { it.key == key }?.let { return@withContext it.reading }

        val past = set.frames
            .filter { latest.time - it.time in 540L..1200L }
            .minByOrNull { abs((latest.time - it.time) - 750L) }
        val geometry = geometry(lat, lon)
        val grids = coroutineScope {
            val currentJob = async { grid(set.host, latest.path, geometry) }
            val pastJob = past?.let { async { grid(set.host, it.path, geometry) } }
            currentJob.await() to pastJob?.await()
        }
        val current = grids.first ?: return@withContext null

        var echoes = 0
        var total = 0
        var cityDbz = NONE
        for (dy in -WINDOW..WINDOW) {
            for (dx in -WINDOW..WINDOW) {
                val x = (geometry.cityX + dx).toInt().coerceIn(0, SIDE - 1)
                val y = (geometry.cityY + dy).toInt().coerceIn(0, SIDE - 1)
                val value = current[y * SIDE + x]
                total++
                if (value >= ECHO_DBZ) echoes++
                if (value > cityDbz) cityDbz = value
            }
        }
        val coverage = echoes.toDouble() / total
        var anyEcho = false
        for (value in current) {
            if (value >= ECHO_DBZ) {
                anyEcho = true
                break
            }
        }

        val currentCells = cells(current, geometry)
        val pastCells = grids.second?.let { cells(it, geometry) }
        val deltaHours = past?.let { (latest.time - it.time) / 3600.0 }
        val track = track(currentCells, pastCells, deltaHours, geometry, age / 60.0)

        val reading = RadarReading(
            wet = coverage >= 0.12,
            strength = coverage,
            track = track,
            cityDbz = max(0.0, cityDbz.toDouble()),
            anyEcho = anyEcho,
            frameEpoch = latest.time
        )
        last = Cached(key, reading)
        reading
    }

    private fun geometry(lat: Double, lon: Double): Geometry {
        val n = 2.0.pow(ZOOM)
        val latRad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val worldX = (lon + 180.0) / 360.0 * n
        val worldY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val originX = floor(worldX).toInt() - RADIUS
        val originY = floor(worldY).toInt() - RADIUS
        return Geometry(
            originX = originX,
            originY = originY,
            cityX = (worldX - originX) * TILE,
            cityY = (worldY - originY) * TILE,
            kmPerPx = 156.54303392 * cos(latRad) / n
        )
    }

    private suspend fun grid(host: String, path: String, geometry: Geometry): FloatArray? = coroutineScope {
        val n = 1 shl ZOOM
        val span = 2 * RADIUS + 1
        val jobs = (0 until span).flatMap { ty ->
            (0 until span).map { tx ->
                async {
                    val tileY = geometry.originY + ty
                    if (tileY < 0 || tileY >= n) {
                        null
                    } else {
                        loadTile(host, path, Math.floorMod(geometry.originX + tx, n), tileY)
                    }
                }
            }
        }
        val tiles = jobs.awaitAll()
        if (tiles[RADIUS * span + RADIUS] == null) return@coroutineScope null
        val grid = FloatArray(SIDE * SIDE) { NONE }
        val memo = HashMap<Int, Float>()
        for (ty in 0 until span) {
            for (tx in 0 until span) {
                val pixels = tiles[ty * span + tx] ?: continue
                for (py in 0 until TILE) {
                    val row = (ty * TILE + py) * SIDE + tx * TILE
                    for (px in 0 until TILE) {
                        grid[row + px] = dbzOf(pixels[py * TILE + px], memo)
                    }
                }
            }
        }
        grid
    }

    private fun loadTile(host: String, path: String, x: Int, y: Int): IntArray? =
        try {
            val request = Request.Builder().url(RadarFrames.reflectivityTileUrl(host, path, ZOOM, x, y)).build()
            RadarFrames.http().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                val options = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@use null
                if (bitmap.width != TILE || bitmap.height != TILE) {
                    bitmap.recycle()
                    return@use null
                }
                val pixels = IntArray(TILE * TILE)
                bitmap.getPixels(pixels, 0, TILE, 0, 0, TILE, TILE)
                bitmap.recycle()
                pixels
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun dbzOf(argb: Int, memo: HashMap<Int, Float>): Float {
        if ((argb ushr 24) < 250) return NONE
        val rgb = argb and 0xFFFFFF
        return memo.getOrPut(rgb) {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            var best = Int.MAX_VALUE
            var index = -1
            for (i in palette.indices) {
                val c = palette[i]
                val dr = r - ((c shr 16) and 0xFF)
                val dg = g - ((c shr 8) and 0xFF)
                val db = b - (c and 0xFF)
                val distance = dr * dr + dg * dg + db * db
                if (distance < best) {
                    best = distance
                    index = i
                }
            }
            if (index < 0 || best > MAX_PALETTE_DISTANCE) NONE else ECHO_DBZ + index
        }
    }

    private fun cells(grid: FloatArray, geometry: Geometry): List<Cell> {
        val seen = BooleanArray(grid.size)
        val queue = IntArray(grid.size)
        val out = ArrayList<Cell>()
        for (start in grid.indices) {
            if (seen[start] || grid[start] < TRACK_DBZ) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var area = 0
            var weightSum = 0.0
            var sumX = 0.0
            var sumY = 0.0
            var peak = NONE
            var edge = Double.MAX_VALUE
            while (head < tail) {
                val p = queue[head++]
                val x = p % SIDE
                val y = p / SIDE
                val value = grid[p]
                area++
                val weight = (value - TRACK_DBZ + 1f).toDouble()
                weightSum += weight
                sumX += weight * x
                sumY += weight * y
                if (value > peak) peak = value
                val distance = hypot(x - geometry.cityX, y - geometry.cityY)
                if (distance < edge) edge = distance
                if (x > 0 && !seen[p - 1] && grid[p - 1] >= TRACK_DBZ) {
                    seen[p - 1] = true
                    queue[tail++] = p - 1
                }
                if (x < SIDE - 1 && !seen[p + 1] && grid[p + 1] >= TRACK_DBZ) {
                    seen[p + 1] = true
                    queue[tail++] = p + 1
                }
                if (y > 0 && !seen[p - SIDE] && grid[p - SIDE] >= TRACK_DBZ) {
                    seen[p - SIDE] = true
                    queue[tail++] = p - SIDE
                }
                if (y < SIDE - 1 && !seen[p + SIDE] && grid[p + SIDE] >= TRACK_DBZ) {
                    seen[p + SIDE] = true
                    queue[tail++] = p + SIDE
                }
            }
            if (area >= MIN_AREA) out += Cell(area, sumX / weightSum, sumY / weightSum, peak, edge)
        }
        return out
    }

    private fun track(
        current: List<Cell>,
        past: List<Cell>?,
        deltaHours: Double?,
        geometry: Geometry,
        ageMinutes: Double
    ): StormTrack? {
        val candidates = current
            .filter { it.edgePx * geometry.kmPerPx <= MAX_RANGE_KM }
            .sortedBy { it.edgePx }
            .take(12)
        if (candidates.isEmpty()) return null
        val tracks = candidates.map { build(it, past, deltaHours, geometry, ageMinutes) }
        return tracks.filter { it.onTarget }.minByOrNull { it.etaMinutes ?: Int.MAX_VALUE } ?: tracks.first()
    }

    private fun match(cell: Cell, past: List<Cell>, deltaHours: Double, geometry: Geometry): Cell? {
        val limit = MAX_SPEED_KMH * deltaHours / geometry.kmPerPx
        return past
            .filter {
                val ratio = it.area.toDouble() / cell.area
                ratio in 0.3..3.3 && hypot(it.x - cell.x, it.y - cell.y) <= limit
            }
            .minByOrNull { hypot(it.x - cell.x, it.y - cell.y) }
    }

    private fun build(
        cell: Cell,
        past: List<Cell>?,
        deltaHours: Double?,
        geometry: Geometry,
        ageMinutes: Double
    ): StormTrack {
        val eastKm = (cell.x - geometry.cityX) * geometry.kmPerPx
        val northKm = -(cell.y - geometry.cityY) * geometry.kmPerPx
        val coreKm = hypot(eastKm, northKm)
        val covered = cell.edgePx <= 1.5
        val edgeKm = if (covered) 0.0 else cell.edgePx * geometry.kmPerPx
        val peak = min(cell.peak, 65f).toDouble()
        val rate = reflectivityToRate(peak)
        val intensity = stormIntensityOf(rate)
        val previous = if (past != null && deltaHours != null && deltaHours > 0.0) match(cell, past, deltaHours, geometry) else null
        if (previous == null || deltaHours == null) {
            return StormTrack(null, null, edgeKm, coreKm, peak, rate, intensity, if (covered) 0 else null, null, covered)
        }
        val vx = (cell.x - previous.x) * geometry.kmPerPx / deltaHours
        val vy = -(cell.y - previous.y) * geometry.kmPerPx / deltaHours
        val speed = hypot(vx, vy)
        val bearing = (Math.toDegrees(atan2(vx, vy)) + 360.0) % 360.0
        if (speed < MIN_SPEED_KMH) {
            return StormTrack(bearing, speed, edgeKm, coreKm, peak, rate, intensity, if (covered) 0 else null, null, covered)
        }
        val radius = sqrt(cell.area * geometry.kmPerPx * geometry.kmPerPx / PI)
        val rx = -eastKm
        val ry = -northKm
        val along = (rx * vx + ry * vy) / (speed * speed)
        val perpendicular = abs(rx * vy - ry * vx) / speed
        val reaches = perpendicular <= radius + 5.0
        val half = if (reaches) sqrt(max(radius * radius - perpendicular * perpendicular, 0.0)).coerceAtLeast(3.0) else 0.0
        if (covered) {
            val exit = along + half / speed
            val duration = if (reaches && exit > 0.0) (exit * 60.0).roundToInt() else null
            return StormTrack(bearing, speed, 0.0, coreKm, peak, rate, intensity, 0, duration, true)
        }
        if (!reaches) {
            return StormTrack(bearing, speed, edgeKm, coreKm, peak, rate, intensity, null, null, false)
        }
        val enterMinutes = (along - half / speed) * 60.0
        if (enterMinutes <= 0.0 || enterMinutes - ageMinutes > HORIZON_MINUTES) {
            return StormTrack(bearing, speed, edgeKm, coreKm, peak, rate, intensity, null, null, false)
        }
        val eta = max(0.0, enterMinutes - ageMinutes).roundToInt()
        val duration = (2.0 * half / speed * 60.0).roundToInt()
        return StormTrack(bearing, speed, edgeKm, coreKm, peak, rate, intensity, eta, duration, true)
    }
}
