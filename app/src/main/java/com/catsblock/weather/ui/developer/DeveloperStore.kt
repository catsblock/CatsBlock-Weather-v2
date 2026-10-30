package com.catsblock.weather.ui.developer

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

private val Context.developerDataStore by preferencesDataStore(name = "developer_settings")
private val EnabledKey = booleanPreferencesKey("enabled")

class DeveloperStore(private val context: Context) {

    val enabled: Flow<Boolean> = context.developerDataStore.data.map { it[EnabledKey] ?: false }

    suspend fun setEnabled(value: Boolean) {
        context.developerDataStore.edit { it[EnabledKey] = value }
    }
}

enum class LogLevel(val letter: String) {
    Info("I"),
    Warning("W"),
    Error("E")
}

data class LogEntry(
    val id: Long,
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object LogRedactor {

    private val secretParam = Regex("(?i)((?:api[_-]?key|x-api-key|key|token|appid|app_id|secret|password|passwd|authorization|auth|session|cookie|signature|sig)\\s*[=:]\\s*)[^&\\s\"',;]+")
    private val bearer = Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-~+/=]+")
    private val locationParam = Regex("(?i)\\b((?:lat|lon|lng|latitude|longitude)\\s*[=:]\\s*)-?[\\d.]+")
    private val email = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val ipv4 = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b")
    private val hexId = Regex("\\b[0-9a-fA-F]{16,}\\b")
    private val longToken = Regex("[A-Za-z0-9_\\-]{28,}")
    private val coordinate = Regex("(?<![\\d.])-?\\d{1,3}\\.\\d{3,}(?![\\d.])")

    fun redact(text: String): String = text
        .replace(secretParam) { it.groupValues[1] + "***" }
        .replace(bearer) { it.groupValues[1] + "***" }
        .replace(locationParam) { it.groupValues[1] + "<coord>" }
        .replace(email, "***")
        .replace(ipv4, "<ip>")
        .replace(hexId, "***")
        .replace(longToken, "***")
        .replace(coordinate, "<coord>")
}

object AppLog {

    private const val MAX_ENTRIES = 600
    private const val MAX_MESSAGE = 700
    private const val CRASH_FILE = "developer_log"
    private const val CRASH_KEY = "last_crash"
    private const val SEPARATOR = '\u001F'

    private val entries = ArrayDeque<LogEntry>()
    private var counter = 0L
    private val changes = MutableStateFlow(0L)

    val revision: StateFlow<Long> = changes
    val startedElapsed: Long = SystemClock.elapsedRealtime()

    fun info(tag: String, message: String) = add(LogLevel.Info, tag, message, null, System.currentTimeMillis())

    fun warn(tag: String, message: String, error: Throwable? = null) =
        add(LogLevel.Warning, tag, message, error, System.currentTimeMillis())

    fun error(tag: String, message: String, error: Throwable? = null) =
        add(LogLevel.Error, tag, message, error, System.currentTimeMillis())

    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        counter++
        changes.value = counter
    }

    fun describe(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        val text = "${root.javaClass.simpleName}: ${root.message.orEmpty()}"
        return text.take(240)
    }

    fun recordCrash(context: Context, thread: Thread, error: Throwable) {
        val trace = error.stackTrace.take(6).joinToString(" < ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
        }
        val now = System.currentTimeMillis()
        add(LogLevel.Error, "Crash", "Uncaught exception on ${thread.name}: ${describe(error)} at $trace", null, now)
        val payload = listOf(thread.name, now.toString(), describe(error), trace).joinToString(SEPARATOR.toString())
        context.getSharedPreferences(CRASH_FILE, Context.MODE_PRIVATE).edit().putString(CRASH_KEY, payload).commit()
    }

    fun restoreCrash(context: Context) {
        val prefs = context.getSharedPreferences(CRASH_FILE, Context.MODE_PRIVATE)
        val saved = prefs.getString(CRASH_KEY, null) ?: return
        prefs.edit().remove(CRASH_KEY).commit()
        val parts = saved.split(SEPARATOR)
        if (parts.size < 4) return
        val time = parts[1].toLongOrNull() ?: System.currentTimeMillis()
        add(
            LogLevel.Error,
            "Crash",
            "Previous session ended unexpectedly on ${parts[0]}: ${parts[2]} at ${parts[3]}",
            null,
            time
        )
    }

    @Synchronized
    private fun add(level: LogLevel, tag: String, message: String, error: Throwable?, time: Long) {
        val text = if (error != null) "$message (${describe(error)})" else message
        if (entries.size >= MAX_ENTRIES) entries.removeFirst()
        counter++
        entries.addLast(
            LogEntry(
                id = counter,
                time = time,
                level = level,
                tag = LogRedactor.redact(tag),
                message = LogRedactor.redact(text).take(MAX_MESSAGE)
            )
        )
        changes.value = counter
    }
}
