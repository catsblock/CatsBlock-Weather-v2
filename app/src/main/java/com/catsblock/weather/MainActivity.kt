package com.catsblock.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catsblock.weather.ui.CityPickerScreen
import com.catsblock.weather.ui.MainScreen
import com.catsblock.weather.ui.MapScreen
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
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: WeatherViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    BackHandler(enabled = screen == Screen.Picker || screen == Screen.Map || screen == Screen.Settings) {
        vm.back()
    }
    when (screen) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        Screen.Welcome -> WelcomeScreen(vm)
        Screen.Picker -> CityPickerScreen(vm)
        Screen.Main -> MainScreen(vm)
        Screen.Map -> MapScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
}
