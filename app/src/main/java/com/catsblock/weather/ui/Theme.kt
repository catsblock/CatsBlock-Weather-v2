package com.catsblock.weather.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.ColorStyle
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.DefaultCustomColor
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.ui.typography.AppFonts

private val LightFallback = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F1F6),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8C2),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFF4F8FF),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF9FBFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE1E8F5),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF8A97AD),
    outlineVariant = Color(0xFFCBD5E5),
    surfaceContainer = Color(0xFFEAF1FC),
    surfaceContainerHigh = Color(0xFFE1EAF8)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF0B4A8C),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFF72D8E4),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFFC8F1F6),
    tertiary = Color(0xFFFFC46B),
    onTertiary = Color(0xFF462B00),
    tertiaryContainer = Color(0xFF653F00),
    onTertiaryContainer = Color(0xFFFFE8C2),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5ECF8),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE5ECF8),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFB6C2D6),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    surfaceContainer = Color(0xFF152033),
    surfaceContainerHigh = Color(0xFF1C2A40)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

private val AmoledBlack = Color.Black
private val AmoledLow = Color(0xFF080808)
private val AmoledContainer = Color(0xFF0E0E0E)
private val AmoledHigh = Color(0xFF151515)
private val AmoledHighest = Color(0xFF1C1C1C)

private fun typographyFor(family: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Light),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Light),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Light),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
}

fun weatherSeed(kind: Condition?, isDay: Boolean): Color = when (kind) {
    null -> Color(0xFF1565C0)
    Condition.Clear -> if (isDay) Color(0xFF1E88E5) else Color(0xFF3949AB)
    Condition.FewClouds -> if (isDay) Color(0xFF2F8FD8) else Color(0xFF3F51B5)
    Condition.MoreClouds -> Color(0xFF4F7DA3)
    Condition.Cloudy -> Color(0xFF5B7083)
    Condition.Fog -> Color(0xFF7A8C99)
    Condition.Drizzle -> Color(0xFF0288D1)
    Condition.Rain -> Color(0xFF1565C0)
    Condition.Storm -> Color(0xFF5E35B1)
    Condition.Sleet -> Color(0xFF4A90B8)
    Condition.Hail -> Color(0xFF3F6FB5)
    Condition.Snow -> Color(0xFF42A5D8)
    Condition.HeavySnow -> Color(0xFF5C9BC4)
    Condition.Blizzard -> Color(0xFF7F9FB5)
}

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))

private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val h = hsv[0]
    val s = hsv[1].coerceIn(0.35f, 0.9f)
    val neutral = 0.14f
    return if (dark) {
        darkColorScheme(
            primary = hsl(h, s, 0.78f),
            onPrimary = hsl(h, s, 0.14f),
            primaryContainer = hsl(h, s * 0.85f, 0.26f),
            onPrimaryContainer = hsl(h, s, 0.90f),
            secondary = hsl(h + 18f, s * 0.45f, 0.76f),
            onSecondary = hsl(h + 18f, s * 0.45f, 0.14f),
            secondaryContainer = hsl(h + 18f, s * 0.4f, 0.26f),
            onSecondaryContainer = hsl(h + 18f, s * 0.45f, 0.90f),
            tertiary = hsl(h + 60f, s * 0.6f, 0.78f),
            onTertiary = hsl(h + 60f, s * 0.6f, 0.14f),
            tertiaryContainer = hsl(h + 60f, s * 0.5f, 0.26f),
            onTertiaryContainer = hsl(h + 60f, s * 0.6f, 0.90f),
            background = hsl(h, neutral, 0.07f),
            onBackground = hsl(h, 0.1f, 0.90f),
            surface = hsl(h, neutral, 0.08f),
            onSurface = hsl(h, 0.1f, 0.90f),
            surfaceVariant = hsl(h, neutral, 0.20f),
            onSurfaceVariant = hsl(h, 0.12f, 0.76f),
            outline = hsl(h, 0.08f, 0.52f),
            outlineVariant = hsl(h, 0.1f, 0.28f),
            surfaceContainerLowest = hsl(h, neutral, 0.05f),
            surfaceContainerLow = hsl(h, neutral, 0.10f),
            surfaceContainer = hsl(h, neutral, 0.13f),
            surfaceContainerHigh = hsl(h, neutral, 0.17f),
            surfaceContainerHighest = hsl(h, neutral, 0.21f)
        )
    } else {
        lightColorScheme(
            primary = hsl(h, s, 0.38f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, s, 0.90f),
            onPrimaryContainer = hsl(h, s, 0.12f),
            secondary = hsl(h + 18f, s * 0.5f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h + 18f, s * 0.5f, 0.90f),
            onSecondaryContainer = hsl(h + 18f, s * 0.5f, 0.12f),
            tertiary = hsl(h + 60f, s * 0.6f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(h + 60f, s * 0.6f, 0.90f),
            onTertiaryContainer = hsl(h + 60f, s * 0.6f, 0.12f),
            background = hsl(h, 0.3f, 0.97f),
            onBackground = hsl(h, 0.2f, 0.10f),
            surface = hsl(h, 0.3f, 0.98f),
            onSurface = hsl(h, 0.2f, 0.10f),
            surfaceVariant = hsl(h, 0.25f, 0.90f),
            onSurfaceVariant = hsl(h, 0.15f, 0.30f),
            outline = hsl(h, 0.1f, 0.50f),
            outlineVariant = hsl(h, 0.15f, 0.80f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.3f, 0.96f),
            surfaceContainer = hsl(h, 0.28f, 0.94f),
            surfaceContainerHigh = hsl(h, 0.26f, 0.92f),
            surfaceContainerHighest = hsl(h, 0.24f, 0.90f)
        )
    }
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceDim = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledLow,
    surfaceContainer = AmoledContainer,
    surfaceContainerHigh = AmoledHigh,
    surfaceContainerHighest = AmoledHighest,
    surfaceVariant = AmoledHigh,
    inverseOnSurface = AmoledBlack
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeatherTheme(
    settings: AppSettings = AppSettings(),
    condition: Condition? = null,
    isDay: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val seed = when (settings.colorStyle) {
        ColorStyle.Weather -> weatherSeed(condition, isDay)
        ColorStyle.Custom -> Color(settings.customColor)
        ColorStyle.Material -> Color(DefaultCustomColor)
    }
    val base = when (settings.colorStyle) {
        ColorStyle.Material ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (dark) DarkFallback else LightFallback
        else -> remember(seed, dark) { seedScheme(seed, dark) }
    }
    val scheme = if (dark && settings.amoled) base.toAmoled() else base
    val family = remember(settings.fontChoice, settings.customFontName) {
        AppFonts.family(context, settings.fontChoice, settings.customFontName)
    }
    val typography = remember(family) { typographyFor(family) }
    val density = LocalDensity.current
    val scaled = remember(density, settings.textScale) {
        Density(density.density, density.fontScale * settings.textScale)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = typography
    ) {
        CompositionLocalProvider(LocalDensity provides scaled, content = content)
    }
}
