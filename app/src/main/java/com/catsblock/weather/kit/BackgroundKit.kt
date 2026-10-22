package com.catsblock.weather.kit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.catsblock.weather.data.Condition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

object BackgroundKit {

    private const val MAX_IMAGE_SIDE = 1920
    private const val MAX_GIF_BYTES = 40L shl 20
    private const val MAX_AUDIO_BYTES = 30L shl 20

    private val visualExtensions = listOf("gif", "jpg")
    private val soundExtensions = listOf("mp3", "m4a", "ogg", "wav", "flac")

    private val state = MutableStateFlow(0)

    val version: StateFlow<Int> = state.asStateFlow()

    private fun visualDir(context: Context): File = File(context.applicationContext.filesDir, "background_kit")

    private fun soundDir(context: Context): File = File(context.applicationContext.filesDir, "sound_kit")

    private fun bump() {
        state.update { it + 1 }
    }

    fun visual(context: Context, kind: Condition, day: Boolean): File? =
        KitFiles.first(visualDir(context), backgroundSlotOf(kind, day).key, visualExtensions)

    fun hasVisual(context: Context, slot: Slot): Boolean =
        KitFiles.first(visualDir(context), slot.key, visualExtensions) != null

    fun sound(context: Context, kind: Condition): File? =
        KitFiles.first(soundDir(context), kind.name, soundExtensions)

    private fun playable(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun audioExtension(mime: String?): String {
        val type = mime.orEmpty().lowercase()
        return when {
            "mp4" in type || "aac" in type || "m4a" in type -> "m4a"
            "ogg" in type -> "ogg"
            "wav" in type -> "wav"
            "flac" in type -> "flac"
            else -> "mp3"
        }
    }

    suspend fun importVisual(context: Context, slot: Slot, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val folder = visualDir(context).apply { mkdirs() }
            val tmp = File(folder, "tmp_${slot.key}.part")
            val extension: String
            if (KitFiles.isGif(context, uri)) {
                if (!KitFiles.copy(context, uri, tmp, MAX_GIF_BYTES)) {
                    tmp.delete()
                    return@runCatching false
                }
                extension = "gif"
            } else {
                val bitmap = KitFiles.decodeBitmap(context, uri, MAX_IMAGE_SIDE) ?: return@runCatching false
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bitmap.recycle()
                extension = "jpg"
            }
            KitFiles.clear(folder, slot.key)
            val saved = tmp.renameTo(File(folder, "${slot.key}.$extension"))
            if (saved) bump() else tmp.delete()
            saved
        }.getOrDefault(false)
    }

    suspend fun importSound(context: Context, kind: Condition, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val folder = soundDir(context).apply { mkdirs() }
            val tmp = File(folder, "tmp_${kind.name}.part")
            if (!KitFiles.copy(context, uri, tmp, MAX_AUDIO_BYTES) || !playable(tmp)) {
                tmp.delete()
                return@runCatching false
            }
            val extension = audioExtension(context.contentResolver.getType(uri))
            KitFiles.clear(folder, kind.name)
            val saved = tmp.renameTo(File(folder, "${kind.name}.$extension"))
            if (saved) bump() else tmp.delete()
            saved
        }.getOrDefault(false)
    }

    suspend fun resetVisual(context: Context, slot: Slot) {
        withContext(Dispatchers.IO) {
            KitFiles.clear(visualDir(context), slot.key)
            bump()
        }
    }

    suspend fun resetSound(context: Context, kind: Condition) {
        withContext(Dispatchers.IO) {
            KitFiles.clear(soundDir(context), kind.name)
            bump()
        }
    }

    suspend fun resetAll(context: Context) {
        withContext(Dispatchers.IO) {
            visualDir(context).listFiles()?.forEach { it.delete() }
            soundDir(context).listFiles()?.forEach { it.delete() }
            bump()
        }
    }
}
