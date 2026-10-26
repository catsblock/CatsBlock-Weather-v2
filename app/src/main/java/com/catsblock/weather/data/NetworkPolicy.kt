package com.catsblock.weather.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkPolicy {

    private fun capabilities(context: Context): NetworkCapabilities? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        return manager.getNetworkCapabilities(network)
    }

    fun onWifi(context: Context): Boolean {
        val caps = capabilities(context) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun quietHoursActive(settings: AppSettings): Boolean {
        if (!settings.quietHours) return false
        val now = java.util.Calendar.getInstance()
        val minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        return minutes in 30 until 360
    }

    fun foregroundAllowed(context: Context, settings: AppSettings): Boolean =
        settings.mobileData || onWifi(context)
}
