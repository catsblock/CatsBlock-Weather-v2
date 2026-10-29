package com.catsblock.weather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.isWet
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.hourLabel
import com.catsblock.weather.ui.label
import com.catsblock.weather.ui.tempText
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class WidgetKind { Compact, Square, Tips, Hourly }

private enum class Glyph { Pin, Shirt, Bike, Plane, Shield }

private class Tip(val glyph: Glyph, val text: String)

private class Palette(val top: Int, val bottom: Int, val glow: Int, val glowX: Float, val glowY: Float)

private val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

private fun c(value: Long): Int = value.toInt()

private fun white(alpha: Float): Int = Color.argb((alpha * 255f).roundToInt(), 255, 255, 255)

private fun palette(kind: Condition, day: Boolean): Palette = if (day) {
    when (kind) {
        Condition.Clear, Condition.FewClouds ->
            Palette(c(0xFF4A7BE3), c(0xFFA7B1EC), c(0xCCFFFFFF), 0.66f, 0.26f)
        Condition.MoreClouds ->
            Palette(c(0xFF5B86D6), c(0xFFA3B4D8), c(0x66FFFFFF), 0.66f, 0.26f)
        Condition.Cloudy ->
            Palette(c(0xFF6C7F9A), c(0xFF9AA8BC), c(0x33FFFFFF), 0.6f, 0.2f)
        Condition.Fog ->
            Palette(c(0xFF7D8A99), c(0xFFAAB3BE), c(0x33FFFFFF), 0.6f, 0.2f)
        Condition.Drizzle, Condition.Rain ->
            Palette(c(0xFF48607F), c(0xFF7B8CA6), c(0x22FFFFFF), 0.6f, 0.2f)
        Condition.Storm ->
            Palette(c(0xFF34394F), c(0xFF5E6480), c(0x14FFFFFF), 0.6f, 0.2f)
        Condition.Sleet, Condition.Hail ->
            Palette(c(0xFF566A86), c(0xFF93A4BA), c(0x22FFFFFF), 0.6f, 0.2f)
        Condition.Snow, Condition.HeavySnow, Condition.Blizzard ->
            Palette(c(0xFF8CA4C4), c(0xFFD3DEEC), c(0x55FFFFFF), 0.6f, 0.2f)
    }
} else {
    when (kind) {
        Condition.Clear, Condition.FewClouds ->
            Palette(c(0xFF0D1B45), c(0xFF2B3C7A), c(0x33CFD8FF), 0.7f, 0.25f)
        Condition.MoreClouds ->
            Palette(c(0xFF16233F), c(0xFF38476B), c(0x22FFFFFF), 0.65f, 0.25f)
        Condition.Cloudy, Condition.Fog ->
            Palette(c(0xFF232B3A), c(0xFF4A5568), c(0x14FFFFFF), 0.6f, 0.2f)
        Condition.Drizzle, Condition.Rain, Condition.Sleet, Condition.Hail ->
            Palette(c(0xFF1A2335), c(0xFF3A4760), c(0x10FFFFFF), 0.6f, 0.2f)
        Condition.Storm ->
            Palette(c(0xFF12141F), c(0xFF2D3248), c(0x10FFFFFF), 0.6f, 0.2f)
        Condition.Snow, Condition.HeavySnow, Condition.Blizzard ->
            Palette(c(0xFF3A4A66), c(0xFF7C8FAF), c(0x22FFFFFF), 0.6f, 0.2f)
    }
}

private fun paintOf(
    size: Float,
    face: Typeface = regular,
    color: Int = Color.WHITE,
    align: Paint.Align = Paint.Align.LEFT
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    typeface = face
    this.color = color
    textAlign = align
    setShadowLayer(size * 0.10f, 0f, size * 0.04f, 0x33000000)
}

private fun Canvas.drawFit(
    value: String,
    x: Float,
    baseline: Float,
    paint: Paint,
    maxWidth: Float = Float.MAX_VALUE
) {
    val shown = if (maxWidth == Float.MAX_VALUE) {
        value
    } else {
        TextUtils.ellipsize(value, TextPaint(paint), maxWidth, TextUtils.TruncateAt.END).toString()
    }
    drawText(shown, x, baseline, paint)
}

private fun buildLayout(value: String, size: Float, width: Float, maxLines: Int, ellipsize: Boolean): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = regular
        color = white(0.92f)
    }
    val builder = StaticLayout.Builder
        .obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
        .setMaxLines(maxLines)
    if (ellipsize) builder.setEllipsize(TextUtils.TruncateAt.END)
    return builder.build()
}

private fun drawWrapped(
    canvas: Canvas,
    value: String,
    x: Float,
    y: Float,
    width: Float,
    startSize: Float,
    minSize: Float,
    maxLines: Int
) {
    var size = startSize
    var layout = buildLayout(value, size, width, Int.MAX_VALUE, false)
    while (layout.lineCount > maxLines && size > minSize) {
        size *= 0.94f
        layout = buildLayout(value, size, width, Int.MAX_VALUE, false)
    }
    layout = buildLayout(value, size, width, maxLines, true)
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
}

private fun drawGlyph(canvas: Canvas, glyph: Glyph, x: Float, y: Float, size: Float, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    canvas.save()
    canvas.translate(x, y)
    val scale = size / 24f
    canvas.scale(scale, scale)
    when (glyph) {
        Glyph.Pin -> {
            val path = Path()
            path.moveTo(12f, 21.5f)
            path.cubicTo(7f, 15.5f, 5f, 12.5f, 5f, 9.2f)
            path.arcTo(RectF(5f, 2.2f, 19f, 16.2f), 180f, 180f, false)
            path.cubicTo(19f, 12.5f, 17f, 15.5f, 12f, 21.5f)
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawCircle(12f, 9.2f, 2.4f, paint)
        }
        Glyph.Shirt -> {
            val path = Path()
            path.moveTo(8f, 3.5f)
            path.lineTo(2.5f, 7f)
            path.lineTo(5f, 11f)
            path.lineTo(8f, 9.5f)
            path.lineTo(8f, 20.5f)
            path.lineTo(16f, 20.5f)
            path.lineTo(16f, 9.5f)
            path.lineTo(19f, 11f)
            path.lineTo(21.5f, 7f)
            path.lineTo(16f, 3.5f)
            path.quadTo(12f, 8f, 8f, 3.5f)
            path.close()
            canvas.drawPath(path, paint)
        }
        Glyph.Bike -> {
            canvas.drawCircle(5.5f, 16f, 3.8f, paint)
            canvas.drawCircle(18.5f, 16f, 3.8f, paint)
            val frame = Path()
            frame.moveTo(5.5f, 16f)
            frame.lineTo(12f, 16f)
            frame.lineTo(9.5f, 8.5f)
            frame.lineTo(5.5f, 16f)
            frame.moveTo(12f, 16f)
            frame.lineTo(15.5f, 8.5f)
            frame.lineTo(9.5f, 8.5f)
            frame.moveTo(15.5f, 8.5f)
            frame.lineTo(18.5f, 16f)
            frame.moveTo(15.5f, 8.5f)
            frame.lineTo(14.8f, 5.5f)
            frame.lineTo(17.2f, 5.5f)
            frame.moveTo(9.5f, 8.5f)
            frame.lineTo(9.5f, 6.6f)
            frame.moveTo(8.2f, 6.6f)
            frame.lineTo(10.8f, 6.6f)
            canvas.drawPath(frame, paint)
        }
        Glyph.Plane -> {
            val path = Path()
            path.moveTo(12f, 2.5f)
            path.lineTo(13.8f, 9f)
            path.lineTo(21.5f, 13.5f)
            path.lineTo(21.5f, 15.5f)
            path.lineTo(13.8f, 13.2f)
            path.lineTo(13.2f, 19f)
            path.lineTo(15.5f, 21f)
            path.lineTo(15.5f, 22f)
            path.lineTo(12f, 21f)
            path.lineTo(8.5f, 22f)
            path.lineTo(8.5f, 21f)
            path.lineTo(10.8f, 19f)
            path.lineTo(10.2f, 13.2f)
            path.lineTo(2.5f, 15.5f)
            path.lineTo(2.5f, 13.5f)
            path.lineTo(10.2f, 9f)
            path.close()
            canvas.drawPath(path, paint)
        }
        Glyph.Shield -> {
            val path = Path()
            path.moveTo(12f, 2.5f)
            path.lineTo(20f, 5.5f)
            path.lineTo(20f, 11.5f)
            path.quadTo(20f, 18.5f, 12f, 21.8f)
            path.quadTo(4f, 18.5f, 4f, 11.5f)
            path.lineTo(4f, 5.5f)
            path.close()
            canvas.drawPath(path, paint)
            canvas.drawLine(12f, 8.5f, 12f, 15f, paint)
            canvas.drawLine(8.8f, 11.8f, 15.2f, 11.8f, paint)
        }
    }
    canvas.restore()
}

private fun drawIcon(
    context: Context,
    canvas: Canvas,
    kind: Condition,
    day: Boolean,
    x: Float,
    y: Float,
    size: Float
) {
    val bitmap = WidgetIcons.cached(context, kind, day)
    if (bitmap == null) {
        if (!day && (kind == Condition.Clear || kind == Condition.FewClouds)) drawMoon(canvas, x, y, size)
        return
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, null, RectF(x, y, x + size, y + size), paint)
}

private fun drawMoon(canvas: Canvas, x: Float, y: Float, size: Float) {
    val r = size * 0.36f
    val cx = x + size / 2f
    val cy = y + size / 2f
    val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    glow.shader = RadialGradient(cx, cy, size * 0.5f, intArrayOf(0x55FFF3C4, Color.TRANSPARENT), floatArrayOf(0.4f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, size * 0.5f, glow)
    val moon = android.graphics.Path().apply { addCircle(cx, cy, r, android.graphics.Path.Direction.CW) }
    val bite = android.graphics.Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.2f, r * 0.86f, android.graphics.Path.Direction.CW) }
    moon.op(bite, android.graphics.Path.Op.DIFFERENCE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFF8DC.toInt(), 0xFFE8D9A8.toInt(), Shader.TileMode.CLAMP)
    canvas.drawPath(moon, paint)
}

private fun drawStars(canvas: Canvas, w: Float, h: Float, density: Float, radius: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var seed = 1234567L
    for (i in 0 until 22) {
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val px = (seed % 1000L) / 1000f
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val py = (seed % 1000L) / 1000f
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val rs = 0.5f + (seed % 100L) / 100f
        val x = radius * 0.4f + px * (w - radius * 0.8f)
        val y = radius * 0.4f + py * (h - radius * 0.8f)
        paint.color = Color.argb((70 + (seed % 120L)).toInt(), 255, 255, 255)
        canvas.drawCircle(x, y, rs * density, paint)
    }
}

private fun drawBackground(canvas: Canvas, w: Float, h: Float, density: Float, palette: Palette, night: Boolean) {
    val radius = 32f * density
    val rect = RectF(0f, 0f, w, h)
    val base = Paint(Paint.ANTI_ALIAS_FLAG)
    base.shader = LinearGradient(0f, 0f, w * 0.4f, h, palette.top, palette.bottom, Shader.TileMode.CLAMP)
    canvas.drawRoundRect(rect, radius, radius, base)
    if (Color.alpha(palette.glow) > 0) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(
            w * palette.glowX,
            h * palette.glowY,
            max(w, h) * 0.62f,
            intArrayOf(palette.glow, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, glow)
    }
    if (night) drawStars(canvas, w, h, density, radius)
    val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    sheen.shader = LinearGradient(0f, 0f, 0f, h, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    canvas.drawRoundRect(rect, radius, radius, sheen)
    val border = Paint(Paint.ANTI_ALIAS_FLAG)
    border.style = Paint.Style.STROKE
    border.strokeWidth = density
    border.color = 0x44FFFFFF
    canvas.drawRoundRect(RectF(density / 2f, density / 2f, w - density / 2f, h - density / 2f), radius, radius, border)
}

private fun drawGlassTile(canvas: Canvas, rect: RectF, radius: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(0.09f) }
    canvas.drawRoundRect(rect, radius, radius, fill)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = white(0.14f)
    }
    canvas.drawRoundRect(rect, radius, radius, outline)
}

private fun drawCity(canvas: Canvas, name: String, x: Float, top: Float, size: Float, maxWidth: Float) {
    val pin = size * 1.05f
    drawGlyph(canvas, Glyph.Pin, x, top + size * 0.05f, pin, white(0.92f))
    val paint = paintOf(size, regular, white(0.92f))
    canvas.drawFit(name, x + pin * 1.25f, top + size * 0.88f, paint, maxWidth - pin * 1.25f)
}

private fun range(s: WidgetSnapshot, fahrenheit: Boolean): String =
    "${tempText(s.low, fahrenheit)}/${tempText(s.high, fahrenheit)}"

private fun buildTips(s: WidgetSnapshot): List<Tip> {
    val feels = s.feels
    val aqi = s.aqi
    val clothing = when {
        feels < 5.0 -> "The current temperature calls for a heavy coat."
        feels < 12.0 -> "The current temperature is suitable for a warm jacket."
        feels < 18.0 -> "The current temperature is suitable for a light jacket."
        feels < 24.0 -> "The current temperature is suitable for long sleeves."
        feels < 31.0 -> "The current temperature is suitable for half sleeves."
        else -> "The current temperature is hot, wear very light clothing."
    }
    val exerciseBad = s.kind.isWet || (aqi != null && aqi > 100.0) || feels > 33.0 || feels < 0.0
    val exercise = when {
        exerciseBad -> "The current conditions are not ideal for outdoor exercise."
        aqi != null -> "The current air quality is good for outdoor exercise."
        else -> "The current weather is good for outdoor exercise."
    }
    val travel = when {
        s.kind == Condition.Storm || s.kind == Condition.Blizzard || s.wind >= 60.0 || s.precip > 10.0 ->
            "The current weather is not suitable for travel."
        s.kind.isWet || s.kind == Condition.Fog || s.wind >= 40.0 || feels >= 36.0 ->
            "The current weather is not very suitable for travel."
        else -> "The current weather is suitable for travel."
    }
    val air = when {
        aqi == null -> if (s.uv >= 6.0) "UV is high, take care of sun protection." else "UV is low, no special protection needed."
        aqi <= 50.0 -> "The air quality is excellent, enjoy the outdoors."
        aqi <= 100.0 -> "The air quality is acceptable."
        aqi <= 150.0 -> "Low air quality, sensitive groups should take care."
        else -> "Low air quality, take care of protection."
    }
    return listOf(
        Tip(Glyph.Shirt, tr(clothing)),
        Tip(Glyph.Bike, tr(exercise)),
        Tip(Glyph.Plane, tr(travel)),
        Tip(Glyph.Shield, tr(air))
    )
}

private fun drawEmpty(canvas: Canvas, w: Float, h: Float) {
    val pad = h * 0.14f
    drawWrapped(canvas, tr("Open the app to set a location."), pad, pad, w - pad * 2f, h * 0.15f, h * 0.07f, 3)
}

private fun drawCompact(context: Context, canvas: Canvas, w: Float, h: Float, s: WidgetSnapshot, f: Boolean) {
    val pad = h * 0.22f
    val temp = paintOf(h * 0.46f, light)
    val cond = paintOf(h * 0.2f, regular, white(0.9f))
    val ascent = temp.textSize * 0.74f
    val top = (h - (ascent + cond.textSize * 1.3f)) / 2f
    val tempBase = top + ascent
    val icon = h * 0.62f
    val iconX = w - pad - icon
    drawCity(canvas, s.city, pad, pad * 0.34f, h * 0.13f, iconX - pad * 1.3f)
    canvas.drawFit(tempText(s.temp, f), pad, tempBase, temp)
    canvas.drawFit(s.liveKind().label(s.liveDay()), pad, tempBase + cond.textSize * 1.25f, cond, w * 0.45f)
    drawIcon(context, canvas, s.liveKind(), s.liveDay(), iconX, (h - icon) / 2f, icon)
    if (w > h * 2.8f) {
        val span = paintOf(h * 0.2f, regular, white(0.9f), Paint.Align.RIGHT)
        canvas.drawFit(range(s, f), iconX - pad * 0.4f, h / 2f + span.textSize * 0.35f, span)
    }
}

private fun drawSquare(context: Context, canvas: Canvas, w: Float, h: Float, s: WidgetSnapshot, f: Boolean) {
    val pad = minOf(w, h) * 0.09f
    var y = pad
    val citySize = h * 0.11f
    val iconSize = h * 0.27f
    drawCity(canvas, s.city, pad, y, citySize, w - pad * 2f - iconSize * 1.05f)
    drawIcon(context, canvas, s.liveKind(), s.liveDay(), w - pad - iconSize, y + citySize * 0.45f, iconSize)
    y += citySize * 1.35f
    val temp = paintOf(h * 0.36f, light)
    canvas.drawFit(tempText(s.temp, f), pad, y + temp.textSize * 0.78f, temp, w * 0.7f)
    y += temp.textSize * 0.95f
    val cond = paintOf(h * 0.13f, medium)
    canvas.drawFit(s.liveKind().label(s.liveDay()), pad, y + cond.textSize * 0.9f, cond, w - pad * 2f)
    y += cond.textSize * 1.3f
    val span = paintOf(h * 0.115f, regular, white(0.88f))
    canvas.drawFit(range(s, f), pad, y + span.textSize * 0.9f, span, w - pad * 2f)
}

private fun drawTips(context: Context, canvas: Canvas, w: Float, h: Float, s: WidgetSnapshot, f: Boolean) {
    val pad = h * 0.10f
    val tempPaint = paintOf(h * 0.2f, regular, Color.WHITE, Paint.Align.RIGHT)
    val tempText = tempText(s.temp, f)
    val tempWidth = tempPaint.measureText(tempText)
    val bandTop = pad * 0.6f
    canvas.drawFit(tempText, w - pad, bandTop + h * 0.2f * 0.8f, tempPaint)
    val icon = h * 0.2f
    val iconX = w - pad - tempWidth - icon * 1.1f
    drawIcon(context, canvas, s.liveKind(), s.liveDay(), iconX, bandTop, icon)
    drawCity(canvas, s.city, pad, pad, h * 0.095f, iconX - pad * 1.5f)

    val gridTop = h * 0.30f
    val rowGap = h * 0.035f
    val rowHeight = (h - gridTop - h * 0.06f - rowGap) / 2f
    val gap = pad
    val columnWidth = (w - pad * 2f - gap) / 2f
    val glyphSize = h * 0.09f
    buildTips(s).forEachIndexed { index, tip ->
        val x = pad + (index % 2) * (columnWidth + gap)
        val y = gridTop + (index / 2) * rowHeight
        val cardTop = y + rowGap * 0.5f
        val cardBottom = y + rowHeight - rowGap * 0.5f
        drawGlassTile(
            canvas,
            RectF(x, cardTop, x + columnWidth, cardBottom),
            h * 0.075f
        )
        val contentX = x + pad * 0.38f
        drawGlyph(canvas, tip.glyph, contentX, cardTop + h * 0.025f, glyphSize, white(0.96f))
        drawWrapped(
            canvas,
            tip.text,
            contentX,
            cardTop + glyphSize + h * 0.035f,
            columnWidth - pad * 0.76f,
            h * 0.078f,
            h * 0.052f,
            2
        )
    }
}

private fun drawHourly(context: Context, canvas: Canvas, w: Float, h: Float, s: WidgetSnapshot, f: Boolean) {
    val pad = h * 0.10f
    val rightWidth = w * 0.4f
    drawCity(canvas, s.city, pad, pad, h * 0.095f, w - pad * 2f - rightWidth)
    val temp = paintOf(h * 0.34f, light)
    canvas.drawFit(tempText(s.temp, f), pad, pad + h * 0.13f + temp.textSize * 0.78f, temp)

    val icon = h * 0.2f
    val iconTop = pad * 0.7f
    drawIcon(context, canvas, s.liveKind(), s.liveDay(), w - pad - icon, iconTop, icon)
    val cond = paintOf(h * 0.095f, regular, white(0.95f), Paint.Align.RIGHT)
    val condBase = iconTop + icon + cond.textSize * 1.05f
    canvas.drawFit(s.liveKind().label(s.liveDay()), w - pad, condBase, cond, rightWidth)
    canvas.drawFit(range(s, f), w - pad, condBase + cond.textSize * 1.25f, cond)

    val hours = s.upcomingHours(6)
    if (hours.isEmpty()) return
    val columnWidth = (w - pad * 2f) / 6f
    val timePaint = paintOf(h * 0.085f, regular, white(0.86f), Paint.Align.CENTER)
    val hourTemp = paintOf(h * 0.1f, medium, Color.WHITE, Paint.Align.CENTER)
    val hourIcon = h * 0.15f
    hours.forEachIndexed { index, hour ->
        val cx = pad + columnWidth * (index + 0.5f)
        drawGlassTile(
            canvas,
            RectF(cx - columnWidth * 0.46f, h * 0.59f, cx + columnWidth * 0.46f, h * 0.98f),
            h * 0.075f
        )
        canvas.drawFit(hourLabel(hour.time), cx, h * 0.64f, timePaint, columnWidth)
        drawIcon(context, canvas, hour.kind, hour.isDay, cx - hourIcon / 2f, h * 0.67f, hourIcon)
        canvas.drawFit(tempText(hour.temp, f), cx, h * 0.93f, hourTemp)
    }
}

object WidgetRenderer {

    fun defaultSize(kind: WidgetKind): Pair<Int, Int> = when (kind) {
        WidgetKind.Compact -> 250 to 80
        WidgetKind.Square -> 130 to 130
        WidgetKind.Tips, WidgetKind.Hourly -> 250 to 130
    }

    fun render(
        context: Context,
        kind: WidgetKind,
        widthDp: Int,
        heightDp: Int,
        snapshot: WidgetSnapshot?,
        settings: AppSettings
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        var density = metrics.density.coerceIn(1f, 3.5f)
        var width = widthDp * density
        var height = heightDp * density
        val maxPixels = metrics.widthPixels.toDouble() * metrics.heightPixels * 0.8
        val pixels = width.toDouble() * height
        if (pixels > maxPixels) {
            val factor = sqrt(maxPixels / pixels).toFloat()
            density *= factor
            width *= factor
            height *= factor
        }
        val bitmap = Bitmap.createBitmap(
            width.roundToInt().coerceAtLeast(1),
            height.roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val data = snapshot
        val colors = if (data == null) palette(Condition.Clear, true) else palette(data.liveKind(), data.liveDay())
        drawBackground(canvas, w, h, density, colors, data != null && !data.liveDay())
        if (data == null) {
            drawEmpty(canvas, w, h)
            return bitmap
        }
        val fahrenheit = settings.fahrenheit
        when (kind) {
            WidgetKind.Compact -> drawCompact(context, canvas, w, h, data, fahrenheit)
            WidgetKind.Square -> drawSquare(context, canvas, w, h, data, fahrenheit)
            WidgetKind.Tips -> drawTips(context, canvas, w, h, data, fahrenheit)
            WidgetKind.Hourly -> drawHourly(context, canvas, w, h, data, fahrenheit)
        }
        return bitmap
    }
}
