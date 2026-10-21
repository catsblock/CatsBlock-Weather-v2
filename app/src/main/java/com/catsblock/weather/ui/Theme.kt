package com.catsblock.weather.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)

@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}
