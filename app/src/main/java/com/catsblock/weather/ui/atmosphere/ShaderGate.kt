package com.catsblock.weather.ui.atmosphere

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

val LocalShadersDisabled = compositionLocalOf { false }

object ShaderGate {
    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @Composable
    @ReadOnlyComposable
    fun enabled(): Boolean = supported && !LocalShadersDisabled.current
}
