package com.catsblock.weather.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.catsblock.weather.data.Condition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

object IconKit {

    private const val MAX_SIDE = 256

    private val state = MutableStateFlow(0)

    val version: StateFlow<Int> = state.asStateFlow()

    private fun dir(context: Context): File = File(context.applicationContext.filesDir, "icon_kit")

    private fun bump() {
        state.update { it + 1 }
    }

    fun custom(context: Context, kind: Condition, day: Boolean): File? {
        val file = File(dir(context), "${iconSlotOf(kind, day).key}.png")
        return if (file.length() > 0L) file else null
    }

    fun hasCustom(context: Context, slot: Slot): Boolean =
        File(dir(context), "${slot.key}.png").length() > 0L

    fun bitmap(context: Context, kind: Condition, day: Boolean): Bitmap? =
        custom(context, kind, day)?.let { BitmapFactory.decodeFile(it.absolutePath) }

    suspend fun import(context: Context, slot: Slot, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = KitFiles.decodeBitmap(context, uri, MAX_SIDE) ?: return@runCatching false
            val folder = dir(context).apply { mkdirs() }
            val tmp = File(folder, "tmp_${slot.key}.part")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            KitFiles.clear(folder, slot.key)
            val saved = tmp.renameTo(File(folder, "${slot.key}.png"))
            if (saved) bump() else tmp.delete()
            saved
        }.getOrDefault(false)
    }

    suspend fun reset(context: Context, slot: Slot) {
        withContext(Dispatchers.IO) {
            KitFiles.clear(dir(context), slot.key)
            bump()
        }
    }

    suspend fun resetAll(context: Context) {
        withContext(Dispatchers.IO) {
            dir(context).listFiles()?.forEach { it.delete() }
            bump()
        }
    }
}
