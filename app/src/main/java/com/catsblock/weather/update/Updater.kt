package com.catsblock.weather.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

private const val REPO = "catsblock/CatsBlock-Weather-v2"

data class UpdateInfo(val build: Int, val versionName: String, val sizeBytes: Long, val url: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Error(val download: Boolean, val info: UpdateInfo?) : UpdateState
}

class UpdateViewModel(private val app: Application) : AndroidViewModel(app) {

    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val client = OkHttpClient()
    private var job: Job? = null

    private fun installedBuild(): Long =
        app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode

    fun check() {
        val s = state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        state.value = UpdateState.Checking
        job = viewModelScope.launch {
            state.value = try {
                withContext(Dispatchers.IO) { fetchLatest() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                UpdateState.Error(false, null)
            }
        }
    }

    private fun updatesDir(): File = File(app.cacheDir, "updates").apply { mkdirs() }

    private fun apkFile(build: Int): File = File(updatesDir(), "update-$build.apk")

    private fun cleanup() {
        val installed = installedBuild()
        updatesDir().listFiles()?.forEach {
            val build = Regex("update-(\\d+)\\.apk").matchEntire(it.name)?.groupValues?.get(1)?.toLongOrNull()
            if (build == null || build <= installed) it.delete()
        }
    }

    private fun cached(info: UpdateInfo): File? =
        apkFile(info.build).takeIf { it.exists() && it.length() == info.sizeBytes }

    private fun fetchLatest(): UpdateState {
        cleanup()
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) return UpdateState.UpToDate
            if (!r.isSuccessful) error("http ${r.code}")
            val o = JSONObject(r.body!!.string())
            val build = Regex("\\d+").find(o.getString("tag_name"))?.value?.toIntOrNull() ?: error("tag")
            if (build <= installedBuild()) return UpdateState.UpToDate
            val assets = o.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return UpdateState.Available(
                        UpdateInfo(build, "1.0.$build", a.getLong("size"), a.getString("browser_download_url"))
                    )
                }
            }
            return UpdateState.UpToDate
        }
    }

    fun update() {
        val info = when (val s = state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.NeedsPermission -> s.info
            is UpdateState.Error -> s.info
            else -> null
        } ?: return
        if (!app.packageManager.canRequestPackageInstalls()) {
            state.value = UpdateState.NeedsPermission(info)
            return
        }
        job = viewModelScope.launch {
            state.value = UpdateState.Downloading(info, 0f)
            try {
                val file = withContext(Dispatchers.IO) { cached(info) ?: download(info) }
                install(file)
                state.value = UpdateState.Available(info)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = UpdateState.Error(true, info)
            }
        }
    }

    private suspend fun download(info: UpdateInfo): File {
        val dir = updatesDir()
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update-${info.build}.apk.part")
        client.newCall(Request.Builder().url(info.url).build()).execute().use { r ->
            if (!r.isSuccessful) error("http ${r.code}")
            val body = r.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var read = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        currentCoroutineContext().ensureActive()
                        out.write(buf, 0, n)
                        read += n
                        if (read - last > 96 * 1024) {
                            last = read
                            state.value = UpdateState.Downloading(info, (read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        val done = apkFile(info.build)
        if (!file.renameTo(done)) error("rename")
        state.value = UpdateState.Downloading(info, 1f)
        return done
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    fun onResume() {
        if (state.value is UpdateState.NeedsPermission && app.packageManager.canRequestPackageInstalls()) update()
    }
}
