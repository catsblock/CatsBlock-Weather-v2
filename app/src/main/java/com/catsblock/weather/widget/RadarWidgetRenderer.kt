package com.catsblock.weather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.catsblock.weather.data.City
import com.catsblock.weather.data.MapStyle
import com.catsblock.weather.data.RadarFrames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

object RadarWidgetRenderer {

    private const val ZOOM = 10
    private const val RADAR_ZOOM = 7
    private const val TILE = 256
    private const val MAX_SIDE = 1200

    @Volatile
    private var http: OkHttpClient? = null

    private fun client(context: Context): OkHttpClient {
        http?.let { return it }
        val built = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cache(Cache(File(context.applicationContext.cacheDir, "radar_tiles"), 20L * 1024L * 1024L))
            .build()
        http = built
        return built
    }

    private fun fetch(client: OkHttpClient, url: String): Bitmap? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "CatsBlockWeather/2.0 (Android)").build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) null
                else response.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
    }.getOrNull()

    private fun tile(client: OkHttpClient, url: String, fallback: String? = null): Bitmap? =
        fetch(client, url) ?: fallback?.let { fetch(client, it) }

    suspend fun render(context: Context, city: City, widthPx: Int, heightPx: Int, alert: String?, dark: Boolean): Bitmap? = coroutineScope {
        val scale = min(1f, MAX_SIDE.toFloat() / max(widthPx, heightPx).toFloat())
        val w = max(2, (widthPx * scale).toInt())
        val h = max(2, (heightPx * scale).toInt())
        val density = context.resources.displayMetrics.density * scale
        val http = client(context)

        val n = 1 shl ZOOM
        val latR = Math.toRadians(city.lat.coerceIn(-85.0, 85.0))
        val tx = n * (city.lon + 180.0) / 360.0
        val ty = n * (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0
        val halfW = w / 2.0 / TILE
        val halfH = h / 2.0 / TILE
        val x0 = floor(tx - halfW).toInt()
        val x1 = floor(tx + halfW).toInt()
        val y0 = floor(ty - halfH).toInt()
        val y1 = floor(ty + halfH).toInt()

        val baseJobs = (y0..y1).filter { it in 0 until n }.flatMap { y ->
            (x0..x1).map { x ->
                async(Dispatchers.IO) { Triple(x, y, tile(http, MapStyle.tileUrl(dark, ZOOM, Math.floorMod(x, n), y), MapStyle.fallbackUrl(ZOOM, Math.floorMod(x, n), y))) }
            }
        }

        val factor = 1 shl (ZOOM - RADAR_ZOOM)
        val rn = 1 shl RADAR_ZOOM
        val radarBase = RadarFrames.base()
        val radarJobs = if (radarBase == null) emptyList() else {
            val rx0 = floor((tx - halfW) / factor).toInt()
            val rx1 = floor((tx + halfW) / factor).toInt()
            val ry0 = floor((ty - halfH) / factor).toInt()
            val ry1 = floor((ty + halfH) / factor).toInt()
            (ry0..ry1).filter { it in 0 until rn }.flatMap { y ->
                (rx0..rx1).map { x ->
                    async(Dispatchers.IO) {
                        Triple(x, y, tile(http, RadarFrames.tileUrl(radarBase, RADAR_ZOOM, Math.floorMod(x, rn), y)))
                    }
                }
            }
        }

        val baseTiles = baseJobs.awaitAll()
        val radarTiles = radarJobs.awaitAll()

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val corner = 22f * density
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), corner, corner, Path.Direction.CW) })
        canvas.drawColor(if (dark) MapStyle.DARK_BASE else MapStyle.LIGHT_BASE)

        val basePaint = if (dark) {
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(MapStyle.DARK_LIFT)) }
        } else {
            null
        }

        baseTiles.forEach { (x, y, bmp) ->
            if (bmp != null) {
                canvas.drawBitmap(
                    bmp,
                    (w / 2.0 + TILE * (x - tx)).toFloat(),
                    (h / 2.0 + TILE * (y - ty)).toFloat(),
                    basePaint
                )
            }
        }

        val radarPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = 204 }
        val radarSize = (TILE * factor).toFloat()
        radarTiles.forEach { (x, y, bmp) ->
            if (bmp != null) {
                val left = (w / 2.0 + TILE * (x * factor - tx)).toFloat()
                val top = (h / 2.0 + TILE * (y * factor - ty)).toFloat()
                canvas.drawBitmap(bmp, null, RectF(left, top, left + radarSize, top + radarSize), radarPaint)
            }
        }

        drawMarker(canvas, w / 2f, h / 2f, density)
        drawBorder(canvas, w, h, density, dark)
        drawFooter(context, canvas, w, h, density, dark)
        if (!alert.isNullOrBlank()) drawAlert(canvas, w, alert, density)
        out
    }

    fun placeholder(widthPx: Int, heightPx: Int, text: String, density: Float, dark: Boolean): Bitmap {
        val w = max(2, widthPx)
        val h = max(2, heightPx)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val corner = 22f * density
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), corner, corner, Path.Direction.CW) })
        canvas.drawColor(if (dark) MapStyle.DARK_BASE else MapStyle.LIGHT_BASE)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFFFFFFFF.toInt() else 0xFF333333.toInt()
            textSize = 13f * density
            textAlign = Paint.Align.CENTER
        }
        val line = TextUtils.ellipsize(text, paint, w - 24f * density, TextUtils.TruncateAt.END).toString()
        canvas.drawText(line, w / 2f, h / 2f + paint.textSize / 3f, paint)
        drawBorder(canvas, w, h, density, dark)
        return out
    }

    private fun drawMarker(canvas: Canvas, cx: Float, cy: Float, density: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x33000000
        canvas.drawCircle(cx, cy + 0.8f * density, 7f * density, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 6f * density, paint)
        paint.color = 0xFF1E88E5.toInt()
        canvas.drawCircle(cx, cy, 4.2f * density, paint)
    }

    private fun drawBorder(canvas: Canvas, w: Int, h: Int, density: Float, dark: Boolean) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = if (dark) 0x30FFFFFF else 0x30000000
        }
        val radius = 22f * density
        canvas.drawRoundRect(RectF(density / 2f, density / 2f, w - density / 2f, h - density / 2f), radius, radius, border)
    }

    private fun drawFooter(context: Context, canvas: Canvas, w: Int, h: Int, density: Float, dark: Boolean) {
        val margin = 8f * density
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f * density
            color = if (dark) 0xCCFFFFFF.toInt() else 0xFF444444.toInt()
        }
        val credit = MapStyle.CREDIT
        val creditWidth = text.measureText(credit)
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0x99202022.toInt() else 0xB3FFFFFF.toInt() }
        val pad = 4f * density
        val creditRect = RectF(
            w - margin - creditWidth - pad * 2,
            h - margin - text.textSize - pad * 1.6f,
            w - margin,
            h - margin
        )
        canvas.drawRoundRect(creditRect, 6f * density, 6f * density, pill)
        canvas.drawText(credit, creditRect.left + pad, creditRect.bottom - pad * 1.1f, text)

        val clock = android.text.format.DateFormat.getTimeFormat(context).format(Date())
        val clockPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f * density
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.DEFAULT_BOLD
        }
        val clockWidth = clockPaint.measureText(clock)
        val clockRect = RectF(
            margin,
            h - margin - clockPaint.textSize - pad * 1.6f,
            margin + clockWidth + pad * 2,
            h - margin
        )
        pill.color = 0x99000000.toInt()
        canvas.drawRoundRect(clockRect, 6f * density, 6f * density, pill)
        canvas.drawText(clock, clockRect.left + pad, clockRect.bottom - pad * 1.1f, clockPaint)
    }

    private fun drawAlert(canvas: Canvas, w: Int, message: String, density: Float) {
        val margin = 8f * density
        val height = 24f * density
        val rect = RectF(margin, margin, w - margin, margin + height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0D32F2F.toInt() }
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        val iconX = rect.left + height / 2f
        canvas.drawCircle(iconX, rect.centerY(), 7f * density, paint)
        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD32F2F.toInt()
            textSize = 10f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("!", iconX, rect.centerY() + mark.textSize / 3f, mark)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 11f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val left = iconX + 12f * density
        val line = TextUtils.ellipsize(message, text, rect.right - left - 10f * density, TextUtils.TruncateAt.END).toString()
        canvas.drawText(line, left, rect.centerY() + text.textSize / 3f, text)
    }
}
