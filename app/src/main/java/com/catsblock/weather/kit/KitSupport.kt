package com.catsblock.weather.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.catsblock.weather.data.Condition
import java.io.File
import kotlin.math.roundToInt

data class Slot(val kind: Condition, val day: Boolean, val paired: Boolean) {
    val key: String get() = "${kind.name}_${if (paired && !day) "n" else "d"}"
}

private val iconPaired: Set<Condition> = setOf(Condition.Clear, Condition.FewClouds, Condition.MoreClouds)

private val backgroundPaired: Set<Condition> = iconPaired + Condition.Fog

private fun buildSlots(paired: Set<Condition>): List<Slot> = Condition.entries.flatMap { kind ->
    if (kind in paired) {
        listOf(Slot(kind, true, true), Slot(kind, false, true))
    } else {
        listOf(Slot(kind, true, false))
    }
}

val iconSlots: List<Slot> = buildSlots(iconPaired)

val backgroundSlots: List<Slot> = buildSlots(backgroundPaired)

fun iconSlotOf(kind: Condition, day: Boolean): Slot = Slot(kind, day, kind in iconPaired)

fun backgroundSlotOf(kind: Condition, day: Boolean): Slot = Slot(kind, day, kind in backgroundPaired)

internal object KitFiles {

    fun first(dir: File, key: String, extensions: List<String>): File? =
        extensions.map { File(dir, "$key.$it") }.firstOrNull { it.length() > 0L }

    fun clear(dir: File, key: String) {
        dir.listFiles { file -> file.nameWithoutExtension == key }?.forEach { it.delete() }
    }

    fun isGif(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 && String(header, Charsets.US_ASCII) == "GIF8"
        } ?: false
    }.getOrDefault(false)

    fun copy(context: Context, uri: Uri, target: File, limit: Long): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        return input.use { source ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                var within = true
                while (within) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > limit) {
                        within = false
                    } else {
                        output.write(buffer, 0, read)
                    }
                }
                within && total > 0L
            }
        }
    }

    fun decodeBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
