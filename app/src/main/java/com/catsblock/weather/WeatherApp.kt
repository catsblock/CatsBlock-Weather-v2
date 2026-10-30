package com.catsblock.weather

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.catsblock.weather.alerts.EclipseAlerts
import com.catsblock.weather.alerts.RainAlerts
import com.catsblock.weather.alerts.SnowAlerts
import com.catsblock.weather.ui.developer.AppLog
import com.catsblock.weather.update.AppUpdates
import com.catsblock.weather.widget.RadarWidgetUpdater
import com.catsblock.weather.widget.RadarWidgetWork
import com.catsblock.weather.work.BackgroundUpdates

class WeatherApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        installLogging()
        RainAlerts.ensureChannel(this)
        EclipseAlerts.ensureChannel(this)
        SnowAlerts.ensureChannel(this)
        AppUpdates.ensureChannel(this)
        BackgroundUpdates.schedule(this)
        if (RadarWidgetUpdater.hasWidgets(this)) RadarWidgetWork.schedule(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLog.warn("Memory", "The system reported low memory")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            AppLog.warn("Memory", "Memory trim requested (level $level)")
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()

    private fun installLogging() {
        AppLog.restoreCrash(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { AppLog.recordCrash(this, thread, error) }
            previous?.uncaughtException(thread, error)
        }
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        AppLog.info("App", "Started version $version on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        registerNetworkLogging()
        registerLifecycleLogging()
    }

    private fun registerNetworkLogging() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                private var lastDescription: String? = null

                override fun onAvailable(network: Network) {
                    AppLog.info("Network", "Connection available")
                }

                override fun onLost(network: Network) {
                    lastDescription = null
                    AppLog.warn("Network", "Connection lost")
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val type = when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        else -> "Other"
                    }
                    val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val description = "$type, metered=$metered, internet=$validated"
                    if (description == lastDescription) return
                    lastDescription = description
                    if (validated) {
                        AppLog.info("Network", "Using $description")
                    } else {
                        AppLog.warn("Network", "Using $description")
                    }
                }
            })
        }
    }

    private fun registerLifecycleLogging() {
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) AppLog.info("Lifecycle", "App moved to the foreground")
            }

            override fun onActivityStopped(activity: Activity) {
                if (--started == 0 && !activity.isChangingConfigurations) {
                    AppLog.info("Lifecycle", "App moved to the background")
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        lateinit var instance: WeatherApp
            private set
    }
}
