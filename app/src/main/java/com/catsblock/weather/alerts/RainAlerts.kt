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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.catsblock.weather.MainActivity
import com.catsblock.weather.R
import com.catsblock.weather.data.City
import com.catsblock.weather.data.Condition
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.isWet
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.trx
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object RainForecast {

    private const val SLOT_MM = 0.1
    private const val PAIR_MM = 0.2

    private fun snowy(kind: Condition): Boolean =
        kind == Condition.Snow || kind == Condition.HeavySnow || kind == Condition.Blizzard

    private fun kindAt(bundle: WeatherBundle, time: LocalDateTime): Condition {
        val key = time.minusMinutes(1).truncatedTo(ChronoUnit.HOURS)
        return bundle.hours.firstOrNull { it.time == key }?.kind ?: bundle.current.kind
    }

    fun nextStart(bundle: WeatherBundle): LocalDateTime? {
        val now = bundle.current.now
        val wetNow = bundle.current.kind.isWet && !snowy(bundle.current.kind)
        var previousWet = wetNow

        if (bundle.rain.isNotEmpty()) {
            val upcoming = bundle.rain.filter { it.time.isAfter(now) }
            upcoming.forEachIndexed { index, slot ->
                val following = upcoming.getOrNull(index + 1)?.mm ?: 0.0
                val wet = slot.mm >= SLOT_MM &&
                    slot.mm + following >= PAIR_MM &&
                    !snowy(kindAt(bundle, slot.time))
                if (wet && !previousWet) {
                    val start = slot.time.minusMinutes(15)
                    return if (start.isBefore(now)) now else start
                }
                previousWet = wet
            }
            return null
        }

        bundle.hours.filter { it.time.isAfter(now) }.forEach { hour ->
            val wet = hour.precip >= SLOT_MM && !snowy(hour.kind)
            if (wet && !previousWet) {
                val start = hour.time.minusMinutes(30)
                return if (start.isBefore(now)) now else start
            }
            previousWet = wet
        }
        return null
    }

    fun nextStartMillis(bundle: WeatherBundle): Long? =
        nextStart(bundle)?.toEpochSecond(ZoneOffset.ofTotalSeconds(bundle.utcOffsetSeconds))?.times(1000L)
}

object RainAlerts {

    private const val CHANNEL = "rain_alerts"
    private const val FILE = "rain_alerts_state"
    private const val WORK = "rain_alert_notify"
    private const val NOTIFICATION_ID = 4101
    private const val TOLERANCE = 30 * 60_000L
    private const val KEY_CITY = "city_id"
    private const val KEY_NAME = "city_name"
    private const val KEY_START = "start"
    private const val KEY_NOTIFIED = "notified"
    private const val KEY_ASKED = "asked"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        prefs(context).edit { putBoolean(KEY_ASKED, true) }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, trx("Rain alerts"), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    suspend fun onFresh(context: Context, city: City, bundle: WeatherBundle) {
        val app = context.applicationContext
        val p = prefs(app)
        val start = RainForecast.nextStartMillis(bundle) ?: 0L
        val sameCity = p.getString(KEY_CITY, null) == city.id
        p.edit {
            if (!sameCity) remove(KEY_NOTIFIED)
            putString(KEY_CITY, city.id)
            putString(KEY_NAME, city.name)
            putLong(KEY_START, start)
        }
        reschedule(app)
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val work = WorkManager.getInstance(app)
        val settings = SettingsStore(app).settings.first()
        val p = prefs(app)
        val start = p.getLong(KEY_START, 0L)
        val now = System.currentTimeMillis()
        val alreadyNotified = abs(start - p.getLong(KEY_NOTIFIED, 0L)) <= TOLERANCE
        if (!settings.rainAlerts || start <= now || alreadyNotified) {
            work.cancelUniqueWork(WORK)
            return
        }
        val fireAt = start - settings.rainLeadMinutes * 60_000L
        val delay = (fireAt - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<RainAlertWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun duration(minutes: Int): String {
        val rounded = ((minutes + 2) / 5 * 5).coerceAtLeast(5)
        val hours = trx("h")
        val mins = trx("min")
        return when {
            rounded < 60 -> "$rounded $mins"
            rounded % 60 == 0 -> "${rounded / 60} $hours"
            else -> "${rounded / 60} $hours ${rounded % 60} $mins"
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun deliver(context: Context) {
        val app = context.applicationContext
        val settings = SettingsStore(app).settings.first()
        val p = prefs(app)
        val start = p.getLong(KEY_START, 0L)
        val now = System.currentTimeMillis()
        if (!settings.rainAlerts || start <= now) return
        if (abs(start - p.getLong(KEY_NOTIFIED, 0L)) <= TOLERANCE) return
        if (start - now > (settings.rainLeadMinutes + 5) * 60_000L) {
            reschedule(app)
            return
        }
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return

        I18n.lang = settings.language
        ensureChannel(app)

        val minutes = ((start - now) / 60_000L).toInt()
        val city = p.getString(KEY_NAME, "").orEmpty()
        val body = String.format(
            settings.language.locale,
            trx("Rain is expected in %1\$s in about %2\$s."),
            city,
            duration(minutes)
        )
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            app,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rain)
            .setContentTitle(trx("Rain is on the way"))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        p.edit { putLong(KEY_NOTIFIED, start) }
    }
}

class RainAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        RainAlerts.deliver(applicationContext)
        return Result.success()
    }
}
