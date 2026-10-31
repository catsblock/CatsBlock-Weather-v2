package com.catsblock.weather.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.data.MapStyle
import com.catsblock.weather.data.TravelPlan
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TRAVEL_TILE = 256

private class TravelView(val zoom: Int, val tx: Double, val ty: Double)

private fun travelTileX(lon: Double, zoom: Int): Double = (1 shl zoom) * (lon + 180.0) / 360.0

private fun travelTileY(lat: Double, zoom: Int): Double {
    val rad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1 shl zoom) * (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
}

private fun fitTravelView(plan: TravelPlan, w: Int, h: Int, pad: Float): TravelView {
    val minLat = plan.path.minOf { it.lat }
    val maxLat = plan.path.maxOf { it.lat }
    val minLon = plan.path.minOf { it.lon }
    val maxLon = plan.path.maxOf { it.lon }
    val zoom = (16 downTo 3).firstOrNull { z ->
        val spanX = (travelTileX(maxLon, z) - travelTileX(minLon, z)) * TRAVEL_TILE
        val spanY = (travelTileY(minLat, z) - travelTileY(maxLat, z)) * TRAVEL_TILE
        spanX <= w - 2 * pad && spanY <= h - 2 * pad
    } ?: 2
    return TravelView(
        zoom,
        (travelTileX(minLon, zoom) + travelTileX(maxLon, zoom)) / 2.0,
        (travelTileY(minLat, zoom) + travelTileY(maxLat, zoom)) / 2.0
    )
}

private object TravelTiles {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val cache = LruCache<String, Bitmap>(96)

    private fun fetch(url: String): Bitmap? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "CatsBlockWeather/2.0 (Android)").build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) null
                else response.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
    }.getOrNull()

    private suspend fun tile(zoom: Int, x: Int, y: Int, dark: Boolean): Bitmap? {
        val url = MapStyle.tileUrl(dark, zoom, x, y)
        cache.get(url)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { fetch(url) ?: fetch(MapStyle.fallbackUrl(zoom, x, y)) }
        if (bitmap != null) cache.put(url, bitmap)
        return bitmap
    }

    suspend fun render(view: TravelView, w: Int, h: Int, dark: Boolean): Bitmap = coroutineScope {
        val n = 1 shl view.zoom
        val baseX = floor(view.tx).toInt()
        val baseY = floor(view.ty).toInt()
        val rx = ceil(w / 2f / TRAVEL_TILE).toInt() + 1
        val ry = ceil(h / 2f / TRAVEL_TILE).toInt() + 1
        val cells = ArrayList<Pair<Int, Int>>()
        for (dy in -ry..ry) {
            val y = baseY + dy
            if (y !in 0 until n) continue
            for (dx in -rx..rx) cells.add((baseX + dx) to y)
        }
        val tiles = cells.map { (x, y) -> async { Triple(x, y, tile(view.zoom, Math.floorMod(x, n), y, dark)) } }.awaitAll()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        canvas.drawColor(if (dark) MapStyle.DARK_BASE else MapStyle.LIGHT_BASE)
        val paint = if (dark) {
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(MapStyle.DARK_LIFT)) }
        } else {
            null
        }
        tiles.forEach { (x, y, bitmap) ->
            if (bitmap != null) {
                val left = (w / 2f + TRAVEL_TILE * (x - view.tx)).roundToInt()
                val top = (h / 2f + TRAVEL_TILE * (y - view.ty)).roundToInt()
                canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), paint)
            }
        }
        out
    }
}

@Composable
internal fun TravelMap(plan: TravelPlan, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = rememberDarkMap()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
    val line = scheme.primary
    val start = scheme.tertiary
    val end = scheme.secondary
    val pad = with(LocalDensity.current) { 40.dp.toPx() }
    BoxWithConstraints(modifier.clipToBounds().background(Color(if (dark) MapStyle.DARK_BASE else MapStyle.LIGHT_BASE))) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val view = remember(plan, w, h) { fitTravelView(plan, w, h, pad) }
        var image by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(view, w, h, dark) {
            if (w > 0 && h > 0) image = TravelTiles.render(view, w, h, dark).asImageBitmap()
        }
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Canvas(Modifier.fillMaxSize()) {
            fun project(lat: Double, lon: Double) = Offset(
                (size.width / 2f + TRAVEL_TILE * (travelTileX(lon, view.zoom) - view.tx)).toFloat(),
                (size.height / 2f + TRAVEL_TILE * (travelTileY(lat, view.zoom) - view.ty)).toFloat()
            )
            val route = Path()
            plan.path.forEachIndexed { i, p ->
                val o = project(p.lat, p.lon)
                if (i == 0) route.moveTo(o.x, o.y) else route.lineTo(o.x, o.y)
            }
            drawPath(route, line.copy(alpha = 0.28f), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route, line, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            plan.stops.forEach { stop ->
                val center = project(stop.point.lat, stop.point.lon)
                val color = when (stop.index) {
                    0 -> start
                    plan.stops.lastIndex -> end
                    else -> line
                }
                drawCircle(Color.White, 15.dp.toPx(), center)
                drawCircle(color, 12.dp.toPx(), center)
                val layout = measurer.measure("${stop.index + 1}", labelStyle)
                drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
            }
        }
        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
