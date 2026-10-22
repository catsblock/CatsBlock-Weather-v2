package com.catsblock.weather.kit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.catsblock.weather.MainActivity
import com.catsblock.weather.R
import com.catsblock.weather.i18n.trx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

object AppIconKit {

    private const val MAX_SIDE = 192

    private val state = MutableStateFlow(0)

    val version: StateFlow<Int> = state.asStateFlow()

    private fun file(context: Context): File =
        File(File(context.applicationContext.filesDir, "app_icon"), "icon.png")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

    fun custom(context: Context): File? = file(context).takeIf { it.length() > 0L }

    fun supported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun hasShortcut(context: Context): Boolean = prefs(context).getString("shortcut_id", null) != null

    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = KitFiles.decodeBitmap(context, uri, MAX_SIDE) ?: return@runCatching false
            val target = file(context)
            target.parentFile?.mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            state.update { it + 1 }
            true
        }.getOrDefault(false)
    }

    private fun rounded(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), side * 0.22f, side * 0.22f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(square, 0f, 0f, paint)
        return out
    }

    private fun disablePrevious(context: Context) {
        val previous = prefs(context).getString("shortcut_id", null) ?: return
        runCatching {
            ShortcutManagerCompat.disableShortcuts(context, listOf(previous), "This icon was removed")
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(previous))
        }
        prefs(context).edit().remove("shortcut_id").apply()
    }

    fun apply(context: Context): Boolean {
        val image = custom(context) ?: return false
        if (!supported(context)) return false
        val bitmap = BitmapFactory.decodeFile(image.absolutePath) ?: return false
        disablePrevious(context)
        val id = "custom_icon_" + System.currentTimeMillis()
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val info = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(R.string.app_name))
            .setIcon(IconCompat.createWithBitmap(rounded(bitmap)))
            .setIntent(intent)
            .build()
        val requested = ShortcutManagerCompat.requestPinShortcut(context, info, null)
        if (requested) prefs(context).edit().putString("shortcut_id", id).apply()
        return requested
    }

    suspend fun reset(context: Context) {
        withContext(Dispatchers.IO) {
            disablePrevious(context)
            file(context).delete()
            state.update { it + 1 }
        }
    }
}
