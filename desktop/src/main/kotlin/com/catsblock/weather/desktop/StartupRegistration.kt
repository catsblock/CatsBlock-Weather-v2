package com.catsblock.weather.desktop

import java.io.File
import java.util.concurrent.TimeUnit

object StartupRegistration {

    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "CatsBlockWeather"
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private fun executable(): String? {
        if (!windows) return null
        val path = ProcessHandle.current().info().command().orElse(null) ?: return null
        val file = File(path)
        if (!file.name.equals("CatsBlockWeather.exe", ignoreCase = true)) return null
        val temp = File(System.getProperty("java.io.tmpdir")).canonicalPath
        return if (file.canonicalPath.startsWith(temp, ignoreCase = true)) null else file.canonicalPath
    }

    val supported: Boolean get() = executable() != null

    private fun reg(vararg args: String): Int = runCatching {
        val process = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        if (process.waitFor(5, TimeUnit.SECONDS)) process.exitValue() else {
            process.destroyForcibly()
            -1
        }
    }.getOrDefault(-1)

    fun isEnabled(): Boolean = windows && reg("query", KEY, "/v", NAME) == 0

    fun setEnabled(enabled: Boolean): Boolean {
        if (!windows) return false
        if (!enabled) return reg("delete", KEY, "/v", NAME, "/f") == 0 || !isEnabled()
        val path = executable() ?: return false
        return reg("add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$path\" --silent", "/f") == 0
    }
}
