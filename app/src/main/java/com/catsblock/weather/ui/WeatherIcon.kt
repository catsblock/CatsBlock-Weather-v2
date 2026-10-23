// ===== WeatherIcon.kt =====
package com.catsblock.weather.ui

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

private var svgLoader: ImageLoader? = null

private fun loader(ctx: Context): ImageLoader =
    svgLoader ?: ImageLoader.Builder(ctx.applicationContext)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()
        .also { svgLoader = it }

@Composable
fun WeatherIcon(type: WeatherType, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    AsyncImage(
        model = type.iconUrl,
        imageLoader = loader(ctx),
        contentDescription = type.label,
        modifier = modifier.size(size)
    )
}
