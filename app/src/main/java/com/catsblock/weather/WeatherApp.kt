package com.catsblock.weather

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.catsblock.weather.work.BackgroundUpdates

class WeatherApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        BackgroundUpdates.schedule(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: WeatherApp
            private set
    }
}
