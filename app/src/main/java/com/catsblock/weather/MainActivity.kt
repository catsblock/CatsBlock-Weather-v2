package com.catsblock.weather

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catsblock.weather.alerts.RainAlerts
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.AppIconScreen
import com.catsblock.weather.ui.AppShell
import com.catsblock.weather.ui.atmosphere.LocalShadersDisabled
import com.catsblock.weather.ui.BackgroundKitScreen
import com.catsblock.weather.ui.CityPickerScreen
import com.catsblock.weather.ui.IconKitScreen
import com.catsblock.weather.ui.LiveMoonScreen
import com.catsblock.weather.ui.PrivacyPolicyScreen
import com.catsblock.weather.ui.Screen
import com.catsblock.weather.ui.WeatherTheme
import com.catsblock.weather.ui.WeatherViewModel
import com.catsblock.weather.ui.WelcomeScreen
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            I18n.lang = deviceLanguage()
            setContent { UnsupportedApiScreen() }
            return
        }
        setContent {
            val vm: WeatherViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            WeatherTheme(settings.theme) {
                I18n.lang = settings.language
                CompositionLocalProvider(LocalShadersDisabled provides settings.disableShaders) {
                    key(settings.language) { AppRoot(vm) }
                }
            }
        }
    }
}

private fun deviceLanguage(): AppLanguage {
    val locale = Locale.getDefault()
    val sameLanguage = AppLanguage.entries.filter { it.locale.language == locale.language }
    return sameLanguage.firstOrNull { it.locale.country == locale.country }
        ?: sameLanguage.firstOrNull()
        ?: AppLanguage.English
}

@Composable
private fun UnsupportedApiScreen() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0A0F17)).padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Warning, null, Modifier.size(56.dp), tint = Color(0xFFFFB74D))
                Spacer(Modifier.height(24.dp))
                Text(
                    tr("Android 13 or newer is required"),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    tr("This app relies on modern Kotlin APIs, the advanced Jetpack Compose graphics pipeline and native runtime shaders (AGSL), none of which exist on Android 12 or earlier. Android 13 (API level 33) or newer is required to run it."),
                    color = Color(0xCCFFFFFF),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "${tr("Installed Android version")}: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    color = Color(0x99FFFFFF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AppRoot(vm: WeatherViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val inShell = screen == Screen.Main || screen == Screen.Map || screen == Screen.Settings
    val onMain = screen == Screen.Main
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(screen, dark) {
        val style = if (screen == Screen.Main || screen == Screen.LiveMoon) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    LaunchedEffect(onMain, settings.rainAlerts, settings.eclipseAlerts, settings.snowAlerts, settings.updateAlerts) {
        val missing = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (onMain && (settings.rainAlerts || settings.eclipseAlerts || settings.snowAlerts || settings.updateAlerts) && missing && !RainAlerts.wasAsked(context)) {
            RainAlerts.markAsked(context)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = screen != null && screen != Screen.Main && screen != Screen.Welcome) {
        vm.back()
    }
    Crossfade(targetState = if (inShell) Screen.Main else screen, animationSpec = tween(320), label = "screen") { target ->
        when (target) {
            null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            Screen.Welcome -> WelcomeScreen(vm)
            Screen.Picker -> CityPickerScreen(vm)
            Screen.Main -> AppShell(vm, screen)
            Screen.Map, Screen.Settings -> AppShell(vm, screen)
            Screen.IconKit -> IconKitScreen(vm)
            Screen.BackgroundKit -> BackgroundKitScreen(vm)
            Screen.AppIcon -> AppIconScreen(vm)
            Screen.LiveMoon -> LiveMoonScreen(vm)
            Screen.Privacy -> PrivacyPolicyScreen(vm)
        }
    }
}
