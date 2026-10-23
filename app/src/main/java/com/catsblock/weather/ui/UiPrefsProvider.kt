package com.catsblock.weather.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.catsblock.weather.data.ApiSources
import com.catsblock.weather.data.FontPreset
import com.catsblock.weather.data.UiPrefs
import com.catsblock.weather.data.UiPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val LocalUiPrefs = compositionLocalOf { UiPrefs() }

class UiActions(
    private val scope: CoroutineScope,
    private val store: UiPrefsStore,
    private val context: Context
) {
    fun update(block: (UiPrefs) -> UiPrefs) {
        scope.launch { store.update(block) }
    }

    fun importFont(uri: Uri) {
        scope.launch {
            val result = FontManager.import(context, uri)
            if (result != null) {
                store.update { it.copy(font = FontPreset.Custom, customName = result.first, customPath = result.second) }
            } else {
                Toast.makeText(context, "Invalid font file. Use .ttf or .otf", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

val LocalUiActions = compositionLocalOf<UiActions> { error("UiActions missing") }

object FontManager {
    private val weights = listOf(
        FontWeight.W200, FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold
    )

    suspend fun import(context: Context, uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            var name = "font"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) name = c.getString(i)
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext != "ttf" && ext != "otf") return@runCatching null
            val dest = File(context.filesDir, "custom_font_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            Typeface.createFromFile(dest)
            context.filesDir.listFiles { f -> f.name.startsWith("custom_font_") && f != dest }?.forEach { it.delete() }
            name to dest.absolutePath
        }.getOrNull()
    }

    fun family(p: UiPrefs): FontFamily {
        if (p.font == FontPreset.Custom) {
            val path = p.customPath ?: return FontFamily.Default
            val f = File(path)
            if (!f.exists()) return FontFamily.Default
            return runCatching { FontFamily(weights.map { Font(f, it) }) }.getOrDefault(FontFamily.Default)
        }
        val d = p.font.device ?: return FontFamily.Default
        return FontFamily(weights.map { Font(DeviceFontFamilyName(d), it) })
    }
}

fun Typography.withFamily(f: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

@Composable
fun ProvideUiPrefs(content: @Composable () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val store = remember { UiPrefsStore(ctx) }
    val scope = rememberCoroutineScope()
    val prefs by store.flow.collectAsState(initial = UiPrefs())
    val actions = remember { UiActions(scope, store, ctx) }
    LaunchedEffect(prefs.api) { ApiSources.current = prefs.api }
    val family = remember(prefs.font, prefs.customPath) { FontManager.family(prefs) }
    val base = MaterialTheme.typography
    val typography = remember(base, family) { base.withFamily(family) }
    CompositionLocalProvider(LocalUiPrefs provides prefs, LocalUiActions provides actions) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}
