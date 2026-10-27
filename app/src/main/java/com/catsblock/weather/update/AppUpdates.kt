package com.catsblock.weather.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.catsblock.weather.MainActivity
import com.catsblock.weather.R
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

object AppUpdates {

    const val ACTION_LATER = "com.catsblock.weather.update.LATER"
    const val ACTION_UPDATE = "com.catsblock.weather.update.UPDATE"
    const val ACTION_STATUS = "com.catsblock.weather.update.STATUS"
    const val KEY_BUILD = "build"
    const val KEY_NAME = "name"
    const val KEY_SIZE = "size"
    const val KEY_URL = "url"

    private const val REPO = "catsblock/CatsBlock-Weather-v2"
    private const val CHANNEL = "app_updates"
    private const val FILE = "app_updates_state"
    private const val KEY_NOTIFIED = "notified_build"
    private const val WORK = "app_update_download"
    const val NOTIFICATION_ID = 4104
    const val PROGRESS_ID = 4105
    const val RESULT_ID = 4106

    private val client = OkHttpClient()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("App updates"), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun channelId(): String = CHANNEL

    fun installedBuild(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun updatesDir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

    fun apkFile(context: Context, build: Int): File = File(updatesDir(context), "update-$build.apk")

    suspend fun latest(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string().orEmpty())
            val build = Regex("\\d+").find(json.getString("tag_name"))?.value?.toIntOrNull()
                ?: return@withContext null
            if (build <= installedBuild(context)) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return@withContext UpdateInfo(
                        build,
                        "1.0.$build",
                        asset.getLong("size"),
                        asset.getString("browser_download_url")
                    )
                }
            }
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun check(context: Context) {
        val app = context.applicationContext
        val settings = SettingsStore(app).settings.first()
        if (!settings.updateAlerts) return
        val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val info = runCatching { latest(app) }.getOrNull() ?: return
        if (prefs.getInt(KEY_NOTIFIED, 0) >= info.build) return
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return

        I18n.lang = settings.language
        ensureChannel(app)

        val body = String.format(settings.language.locale, tr("Version %1\$s is ready to download."), info.versionName)
        val open = PendingIntent.getActivity(
            app,
            10,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val later = PendingIntent.getBroadcast(
            app,
            11,
            Intent(app, UpdateReceiver::class.java).setAction(ACTION_LATER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val update = PendingIntent.getBroadcast(
            app,
            12,
            infoIntent(app, info).setAction(ACTION_UPDATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(tr("New app update"))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, tr("Later"), later)
            .addAction(0, tr("Update"), update)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        prefs.edit { putInt(KEY_NOTIFIED, info.build) }
    }

    fun infoIntent(context: Context, info: UpdateInfo): Intent =
        Intent(context, UpdateReceiver::class.java)
            .putExtra(KEY_BUILD, info.build)
            .putExtra(KEY_NAME, info.versionName)
            .putExtra(KEY_SIZE, info.sizeBytes)
            .putExtra(KEY_URL, info.url)

    fun startDownload(context: Context, data: Data) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

class UpdateReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            AppUpdates.ACTION_LATER -> {
                NotificationManagerCompat.from(app).cancel(AppUpdates.NOTIFICATION_ID)
            }
            AppUpdates.ACTION_UPDATE -> {
                NotificationManagerCompat.from(app).cancel(AppUpdates.NOTIFICATION_ID)
                AppUpdates.startDownload(
                    app,
                    workDataOf(
                        AppUpdates.KEY_BUILD to intent.getIntExtra(AppUpdates.KEY_BUILD, 0),
                        AppUpdates.KEY_NAME to intent.getStringExtra(AppUpdates.KEY_NAME).orEmpty(),
                        AppUpdates.KEY_SIZE to intent.getLongExtra(AppUpdates.KEY_SIZE, 0L),
                        AppUpdates.KEY_URL to intent.getStringExtra(AppUpdates.KEY_URL).orEmpty()
                    )
                )
            }
            AppUpdates.ACTION_STATUS -> {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val manager = NotificationManagerCompat.from(app)
                val name = intent.getStringExtra(AppUpdates.KEY_NAME).orEmpty()
                I18n.lang = runBlocking { SettingsStore(app).settings.first() }.language
                manager.cancel(AppUpdates.PROGRESS_ID)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val raw: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        val confirm = raw ?: return
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { app.startActivity(confirm) }
                        val pending = PendingIntent.getActivity(
                            app,
                            13,
                            confirm,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val text = String.format(
                            I18n.lang.locale,
                            tr("Tap to install version %1\$s."),
                            name
                        )
                        manager.notify(
                            AppUpdates.RESULT_ID,
                            NotificationCompat.Builder(app, AppUpdates.channelId())
                                .setSmallIcon(R.drawable.ic_stat_update)
                                .setContentTitle(tr("Update ready"))
                                .setContentText(text)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setContentIntent(pending)
                                .setAutoCancel(true)
                                .build()
                        )
                    }
                    PackageInstaller.STATUS_SUCCESS -> manager.cancel(AppUpdates.RESULT_ID)
                    else -> manager.notify(
                        AppUpdates.RESULT_ID,
                        NotificationCompat.Builder(app, AppUpdates.channelId())
                            .setSmallIcon(R.drawable.ic_stat_update)
                            .setContentTitle(tr("Update failed"))
                            .setContentText(tr("The update could not be installed. Try again from Settings."))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }
        }
    }
}

class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val client = OkHttpClient()

    private fun progressNotification(name: String, progress: Int) =
        NotificationCompat.Builder(applicationContext, AppUpdates.channelId())
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(tr("Downloading update..."))
            .setContentText(name)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress == 0)
            .build()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val name = inputData.getString(AppUpdates.KEY_NAME).orEmpty()
        val notification = progressNotification(name, 0)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(AppUpdates.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(AppUpdates.PROGRESS_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext
        val settings = SettingsStore(app).settings.first()
        I18n.lang = settings.language
        AppUpdates.ensureChannel(app)

        val build = inputData.getInt(AppUpdates.KEY_BUILD, 0)
        val name = inputData.getString(AppUpdates.KEY_NAME).orEmpty()
        val size = inputData.getLong(AppUpdates.KEY_SIZE, 0L)
        val url = inputData.getString(AppUpdates.KEY_URL).orEmpty()
        if (build <= 0 || url.isEmpty()) return Result.success()

        val manager = NotificationManagerCompat.from(app)

        if (!app.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                app,
                14,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val retry = PendingIntent.getBroadcast(
                app,
                12,
                AppUpdates.infoIntent(app, UpdateInfo(build, name, size, url)).setAction(AppUpdates.ACTION_UPDATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.notify(
                AppUpdates.RESULT_ID,
                NotificationCompat.Builder(app, AppUpdates.channelId())
                    .setSmallIcon(R.drawable.ic_stat_update)
                    .setContentTitle(tr("Allow installing updates"))
                    .setContentText(tr("Allow this app to install updates, then tap Update again."))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pending)
                    .addAction(0, tr("Update"), retry)
                    .setAutoCancel(true)
                    .build()
            )
            return Result.success()
        }

        return try {
            runCatching { setForeground(getForegroundInfo()) }
            val file = AppUpdates.apkFile(app, build)
            if (!file.exists() || file.length() != size) download(url, build, size, name)
            install(file, name)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            manager.cancel(AppUpdates.PROGRESS_ID)
            manager.notify(
                AppUpdates.RESULT_ID,
                NotificationCompat.Builder(app, AppUpdates.channelId())
                    .setSmallIcon(R.drawable.ic_stat_update)
                    .setContentTitle(tr("Update failed"))
                    .setContentText(tr("The update could not be installed. Try again from Settings."))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
            )
            Result.success()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun download(url: String, build: Int, size: Long, name: String) {
        val app = applicationContext
        val dir = AppUpdates.updatesDir(app)
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "update-$build.apk.part")
        val manager = NotificationManagerCompat.from(app)
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("http ${response.code}")
                val body = response.body ?: error("body")
                val total = body.contentLength().takeIf { it > 0 } ?: size
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(32 * 1024)
                        var read = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            currentCoroutineContext().ensureActive()
                            out.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    manager.notify(AppUpdates.PROGRESS_ID, progressNotification(name, percent))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!part.renameTo(AppUpdates.apkFile(app, build))) error("rename")
    }

    private fun install(file: File, name: String) {
        val app = applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        params.setSize(file.length())
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val status = PendingIntent.getBroadcast(
                app,
                id,
                Intent(app, UpdateReceiver::class.java)
                    .setAction(AppUpdates.ACTION_STATUS)
                    .putExtra(AppUpdates.KEY_NAME, name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(status.intentSender)
        }
    }
}
