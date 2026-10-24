package com.catsblock.weather.alerts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.catsblock.weather.MainActivity
import com.catsblock.weather.R
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.City
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.trx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Shared machinery for "tell me N minutes before X starts" notifications.
 * Same behaviour as [RainAlerts]: state is kept in SharedPreferences, a one-time
 * WorkManager job fires at (start - lead), and each start is only notified once.
 */
abstract class LeadAlert(
    private val channel: String,
    private val file: String,
    private val workName: String,
    private val notificationId: Int,
    private val requestCode: Int,
    private val tolerance: Long
) {
    private val keyCity = "city_id"
    private val keyName = "city_name"
    private val keyStart = "start"
    private val keyKind = "kind"
    private val keyNotified = "notified"

    protected abstract fun channelName(): String
    protected abstract fun enabled(settings: AppSettings): Boolean
    protected abstract fun leadMinutes(settings: AppSettings): Int
    protected abstract fun title(kind: Int): String
    protected abstract fun body(kind: Int, city: String, minutes: Int): String
    protected abstract fun newRequest(delayMs: Long): OneTimeWorkRequest

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(channel, channelName(), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    protected fun store(context: Context, city: City, startMs: Long, kind: Int) {
        val p = prefs(context)
        val sameCity = p.getString(keyCity, null) == city.id
        p.edit {
            if (!sameCity) remove(keyNotified)
            putString(keyCity, city.id)
            putString(keyName, city.name)
            putLong(keyStart, startMs)
            putInt(keyKind, kind)
        }
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val work = WorkManager.getInstance(app)
        val settings = SettingsStore(app).settings.first()
        val p = prefs(app)
        val start = p.getLong(keyStart, 0L)
        val now = System.currentTimeMillis()
        val alreadyNotified = abs(start - p.getLong(keyNotified, 0L)) <= tolerance
        if (!enabled(settings) || start <= now || alreadyNotified) {
            work.cancelUniqueWork(workName)
            return
        }
        val fireAt = start - leadMinutes(settings) * 60_000L
        val delay = (fireAt - now).coerceAtLeast(0L)
        work.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, newRequest(delay))
    }

    @SuppressLint("MissingPermission")
    suspend fun deliver(context: Context) {
        val app = context.applicationContext
        val settings = SettingsStore(app).settings.first()
        val p = prefs(app)
        val start = p.getLong(keyStart, 0L)
        val now = System.currentTimeMillis()
        if (!enabled(settings) || start <= now) return
        if (abs(start - p.getLong(keyNotified, 0L)) <= tolerance) return
        if (start - now > (leadMinutes(settings) + 5) * 60_000L) {
            reschedule(app)
            return
        }
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return

        I18n.lang = settings.language
        ensureChannel(app)

        val minutes = ((start - now) / 60_000L).toInt()
        val city = p.getString(keyName, "").orEmpty()
        val kind = p.getInt(keyKind, 0)
        val text = body(kind, city, minutes)
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            app,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_stat_rain)
            .setContentTitle(title(kind))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
        p.edit { putLong(keyNotified, start) }
    }

    protected fun delayedRequest(builder: OneTimeWorkRequest.Builder, delayMs: Long): OneTimeWorkRequest =
        builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
}

object EclipseAlerts : LeadAlert(
    channel = "eclipse_alerts",
    file = "eclipse_alerts_state",
    workName = "eclipse_alert_notify",
    notificationId = 4102,
    requestCode = 8,
    tolerance = 60 * 60_000L
) {
    override fun channelName() = trx("Eclipse alerts")
    override fun enabled(settings: AppSettings) = settings.eclipseAlerts
    override fun leadMinutes(settings: AppSettings) = settings.eclipseLeadMinutes

    override fun title(kind: Int) =
        if (kind == EclipseType.Solar.ordinal) trx("Solar eclipse ahead") else trx("Lunar eclipse ahead")

    override fun body(kind: Int, city: String, minutes: Int): String {
        val template = if (kind == EclipseType.Solar.ordinal) {
            trx("A solar eclipse will be visible in %1\$s in about %2\$s.")
        } else {
            trx("A lunar eclipse will be visible in %1\$s in about %2\$s.")
        }
        return String.format(I18n.lang.locale, template, city, RainAlerts.duration(minutes))
    }

    override fun newRequest(delayMs: Long): OneTimeWorkRequest =
        delayedRequest(OneTimeWorkRequestBuilder<EclipseAlertWorker>(), delayMs)

    suspend fun onFresh(context: Context, city: City) {
        val app = context.applicationContext
        val event = withContext(Dispatchers.Default) {
            EclipseForecast.next(System.currentTimeMillis(), city.lat, city.lon)
        }
        store(app, city, event?.startMs ?: 0L, event?.type?.ordinal ?: 0)
        reschedule(app)
    }
}

object SnowAlerts : LeadAlert(
    channel = "snow_alerts",
    file = "snow_alerts_state",
    workName = "snow_alert_notify",
    notificationId = 4103,
    requestCode = 9,
    tolerance = 30 * 60_000L
) {
    override fun channelName() = trx("Snow alerts")
    override fun enabled(settings: AppSettings) = settings.snowAlerts
    override fun leadMinutes(settings: AppSettings) = settings.snowLeadMinutes

    override fun title(kind: Int) = trx("Snow is on the way")

    override fun body(kind: Int, city: String, minutes: Int): String =
        String.format(I18n.lang.locale, trx("Snow is expected in %1\$s in about %2\$s."), city, RainAlerts.duration(minutes))

    override fun newRequest(delayMs: Long): OneTimeWorkRequest =
        delayedRequest(OneTimeWorkRequestBuilder<SnowAlertWorker>(), delayMs)

    suspend fun onFresh(context: Context, city: City, bundle: WeatherBundle) {
        val app = context.applicationContext
        store(app, city, SnowForecast.nextStartMillis(bundle) ?: 0L, 0)
        reschedule(app)
    }
}

class EclipseAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EclipseAlerts.deliver(applicationContext)
        return Result.success()
    }
}

class SnowAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SnowAlerts.deliver(applicationContext)
        return Result.success()
    }
}
