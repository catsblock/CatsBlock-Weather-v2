package com.catsblock.weather.ui.typography

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.catsblock.weather.data.FontChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppFonts {

    val pickerMimeTypes = arrayOf(
        "font/ttf",
        "font/otf",
        "font/sfnt",
        "application/x-font-ttf",
        "application/x-font-otf",
        "application/x-font-opentype",
        "application/font-sfnt",
        "application/octet-stream"
    )

    private const val MAX_BYTES = 20L * 1024L * 1024L
    private val allowedExtensions = setOf("ttf", "otf")

    private const val FILE_PREFIX = "custom_font_"

    fun displayName(stored: String): String = stored.substringAfter('|', stored)

    private fun storedFile(context: Context, stored: String): File? {
        val name = stored.substringBefore('|', "")
        return if (name.startsWith(FILE_PREFIX)) File(context.filesDir, name) else null
    }

    fun family(context: Context, choice: FontChoice, customFontName: String): FontFamily = when (choice) {
        FontChoice.GoogleSans -> GoogleSansFlex.family
        FontChoice.System -> FontFamily.Default
        FontChoice.Condensed -> device("sans-serif-condensed")
        FontChoice.Serif -> FontFamily.Serif
        FontChoice.Monospace -> FontFamily.Monospace
        FontChoice.Casual -> device("casual")
        FontChoice.Cursive -> FontFamily.Cursive
        FontChoice.Custom -> custom(context, customFontName)
    }

    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(context, uri) ?: return@runCatching null
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension !in allowedExtensions) return@runCatching null
            val temp = File(context.cacheDir, "font_import.tmp")
            var copied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_BYTES) {
                            temp.delete()
                            return@runCatching null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@runCatching null
            if (Typeface.Builder(temp).build() == null) {
                temp.delete()
                return@runCatching null
            }
            val fileName = FILE_PREFIX + System.currentTimeMillis() + ".bin"
            val target = File(context.filesDir, fileName)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            context.filesDir.listFiles()
                ?.filter { it.name.startsWith(FILE_PREFIX) && it.name != fileName }
                ?.forEach { it.delete() }
            fileName + "|" + name.substringBeforeLast('.')
        }.getOrNull()
    }

    private fun custom(context: Context, stored: String): FontFamily {
        val file = storedFile(context, stored)
        if (file == null || !file.exists()) return GoogleSansFlex.family
        return runCatching {
            FontFamily(
                Font(file, FontWeight.Normal),
                Font(file, FontWeight.Bold)
            )
        }.getOrDefault(GoogleSansFlex.family)
    }

    private fun device(name: String): FontFamily = FontFamily(
        Font(DeviceFontFamilyName(name), FontWeight.Normal),
        Font(DeviceFontFamilyName(name), FontWeight.Medium),
        Font(DeviceFontFamilyName(name), FontWeight.Bold)
    )

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
