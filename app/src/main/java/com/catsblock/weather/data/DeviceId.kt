package com.catsblock.weather.data

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

object DeviceId {

    @Volatile
    private var cached: String? = null

    fun get(context: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = context.applicationContext
            val raw = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                ?: fallback(app)
            val id = MessageDigest.getInstance("SHA-256")
                .digest("catsblock:$raw".toByteArray())
                .joinToString("") { "%02x".format(it) }
            cached = id
            return id
        }
    }

    private fun fallback(app: Context): String {
        val prefs = app.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }
}
