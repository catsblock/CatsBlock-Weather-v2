package com.catsblock.weather

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.catsblock.weather.alerts.EclipseAlerts
import com.catsblock.weather.alerts.RainAlerts
import com.catsblock.weather.alerts.SnowAlerts
import com.catsblock.weather.update.AppUpdates
import com.catsblock.weather.work.BackgroundUpdates

class WeatherApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        RainAlerts.ensureChannel(this)
        EclipseAlerts.ensureChannel(this)
        SnowAlerts.ensureChannel(this)
        AppUpdates.ensureChannel(this)
        BackgroundUpdates.schedule(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: WeatherApp
            private set
    }
}
