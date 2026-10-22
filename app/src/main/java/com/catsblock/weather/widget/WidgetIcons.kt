package com.catsblock.weather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.catsblock.weather.data.Condition
import com.catsblock.weather.ui.iconUrl
import java.io.File

object WidgetIcons {

    private const val SIZE = 160

    private fun file(context: Context, kind: Condition, day: Boolean): File {
        val dir = File(context.cacheDir, "widget_icons").apply { mkdirs() }
        return File(dir, "${kind.name}_${if (day) "d" else "n"}.png")
    }

    fun cached(context: Context, kind: Condition, day: Boolean): Bitmap? {
        val target = file(context, kind, day)
        if (!target.exists()) return null
        return BitmapFactory.decodeFile(target.absolutePath)
    }

    suspend fun prefetch(context: Context, items: Collection<Pair<Condition, Boolean>>) {
        items.distinct().forEach { (kind, day) ->
            val target = file(context, kind, day)
            if (target.exists()) return@forEach
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(iconUrl(kind, day))
                    .size(SIZE)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result as? SuccessResult)?.drawable
                    ?.toBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                if (bitmap != null) {
                    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }
}
