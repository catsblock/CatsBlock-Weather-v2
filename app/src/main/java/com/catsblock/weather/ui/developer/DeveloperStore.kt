package com.catsblock.weather.ui.developer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.developerDataStore by preferencesDataStore(name = "developer_settings")
private val EnabledKey = booleanPreferencesKey("enabled")

class DeveloperStore(private val context: Context) {

    val enabled: Flow<Boolean> = context.developerDataStore.data.map { it[EnabledKey] ?: false }

    suspend fun setEnabled(value: Boolean) {
        context.developerDataStore.edit { it[EnabledKey] = value }
    }
}

object LogRedactor {

    private val secretParam = Regex("(?i)((?:api[_-]?key|key|token|appid|secret|password|authorization|bearer)\\s*[=:]\\s*)[^&\\s\"',;]+")
    private val coordinate = Regex("-?\\d{1,3}\\.\\d{3,}")
    private val longToken = Regex("[A-Za-z0-9_\\-]{28,}")
    private val email = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    fun redact(text: String): String = text
        .replace(secretParam) { it.groupValues[1] + "***" }
        .replace(email, "***")
        .replace(longToken, "***")
        .replace(coordinate, "<coord>")
}
