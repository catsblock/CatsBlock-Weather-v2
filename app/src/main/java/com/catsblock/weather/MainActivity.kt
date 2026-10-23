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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.ui.AppIconScreen
import com.catsblock.weather.ui.BackgroundKitScreen
import com.catsblock.weather.ui.CityPickerScreen
import com.catsblock.weather.ui.IconKitScreen
import com.catsblock.weather.ui.LocalAppTheme
import com.catsblock.weather.ui.LiveMoonScreen
import com.catsblock.weather.ui.MainScreen
import com.catsblock.weather.ui.Screen
import com.catsblock.weather.ui.SettingsScreen
import com.catsblock.weather.ui.WeatherTheme
import com.catsblock.weather.ui.WeatherViewModel
import com.catsblock.weather.ui.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherTheme {
                val vm: WeatherViewModel = viewModel()
                val settings by vm.settings.collectAsStateWithLifecycle()
                I18n.lang = settings.language
                CompositionLocalProvider(LocalAppTheme provides settings.theme) {
                    key(settings.language) { AppRoot(vm) }
                }
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
    val dark = isSystemInDarkTheme()
    val onMain = screen == Screen.Main
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(screen, dark) {
        val style = if (screen == Screen.Main || screen == Screen.LiveMoon) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    LaunchedEffect(onMain, settings.rainAlerts) {
        val missing = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (onMain && settings.rainAlerts && missing && !RainAlerts.wasAsked(context)) {
            RainAlerts.markAsked(context)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = screen != null && screen != Screen.Main && screen != Screen.Welcome) {
        vm.back()
    }
    when (screen) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        Screen.Welcome -> WelcomeScreen(vm)
        Screen.Picker -> CityPickerScreen(vm)
        Screen.Main -> MainScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.IconKit -> IconKitScreen(vm)
        Screen.BackgroundKit -> BackgroundKitScreen(vm)
        Screen.AppIcon -> AppIconScreen(vm)
        Screen.LiveMoon -> LiveMoonScreen(vm)
    }
}
