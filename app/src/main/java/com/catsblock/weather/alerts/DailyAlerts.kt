package com.catsblock.weather.alerts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.catsblock.weather.MainActivity
import com.catsblock.weather.R
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.City
import com.catsblock.weather.data.GovAlert
import com.catsblock.weather.data.NetworkPolicy
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.data.UserKeys
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.WeatherRepository
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.alerts.AlertState
import com.catsblock.weather.ui.alerts.AlertStore
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class DailyKind(val channel: String, val notificationId: Int, val requestCode: Int) {
    Clothing("clothing_alerts", 4201, 21),
    Uv("uv_alerts", 4202, 22),
    Pollen("pollen_alerts", 4203, 23),
    Pill("pill_alerts", 4204, 24),
    Frost("frost_alerts", 4205, 25),
    Briefing("briefing_alerts", 4206, 26),
    Pressure("pressure_alerts", 4207, 27),
    Aqi("aqi_alerts", 4208, 28)
}

private class Notice(val title: String, val text: String, val big: String = text)

object DailyAlerts {

    const val KEY_KIND = "kind"
    private const val STATE_FILE = "daily_alerts_state"
    private const val KEY_SEEN = "official_seen"
    private const val OFFICIAL_CHANNEL = "official_alerts"
    private const val OFFICIAL_ID_BASE = 5000
    private const val OFFICIAL_REQUEST = 29
    private const val GRACE_MINUTES = 240L

    private fun isEnabled(kind: DailyKind, s: AlertState): Boolean = when (kind) {
        DailyKind.Clothing -> s.clothingOn
        DailyKind.Uv -> s.uvOn
        DailyKind.Pollen -> s.pollenOn
        DailyKind.Pill -> s.pillOn
        DailyKind.Frost -> s.frostOn
        DailyKind.Briefing -> s.briefingOn
        DailyKind.Pressure -> s.pressureOn
        DailyKind.Aqi -> s.aqiOn
    }

    private fun minutesOf(kind: DailyKind, s: AlertState): Int = when (kind) {
        DailyKind.Clothing -> s.clothingTime
        DailyKind.Uv -> s.uvTime
        DailyKind.Pollen -> s.pollenTime
        DailyKind.Pill -> s.pillTime
        DailyKind.Frost -> s.frostTime
        DailyKind.Briefing -> s.briefingTime
        DailyKind.Pressure -> s.pressureTime
        DailyKind.Aqi -> s.aqiTime
    }

    private fun channelName(kind: DailyKind): String = tr(
        when (kind) {
            DailyKind.Clothing -> "Clothing consultant"
            DailyKind.Uv -> "UV index alerts"
            DailyKind.Pollen -> "Pollen alerts"
            DailyKind.Pill -> "Allergy pill reminder"
            DailyKind.Frost -> "Frost alerts"
            DailyKind.Briefing -> "Tomorrow weather briefing"
            DailyKind.Pressure -> "Pressure forecast alerts"
            DailyKind.Aqi -> "Air quality alerts"
        }
    )

    private fun workName(kind: DailyKind) = "daily_alert_${kind.name.lowercase()}"

    private fun delayUntil(minutes: Int): Long {
        val now = ZonedDateTime.now()
        var target = now.toLocalDate().atTime(minutes / 60 % 24, minutes % 60).atZone(now.zone)
        if (!target.isAfter(now.plusSeconds(30))) {
            target = now.toLocalDate().plusDays(1).atTime(minutes / 60 % 24, minutes % 60).atZone(now.zone)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        DailyKind.entries.forEach { kind ->
            manager.createNotificationChannel(
                NotificationChannel(kind.channel, channelName(kind), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        manager.createNotificationChannel(
            NotificationChannel(OFFICIAL_CHANNEL, tr("Official meteorological warnings"), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    suspend fun reschedule(context: Context, replace: Boolean = true) {
        val app = context.applicationContext
        val state = AlertStore(app).flow.first()
        val work = WorkManager.getInstance(app)
        DailyKind.entries.forEach { kind ->
            if (isEnabled(kind, state)) {
                enqueue(app, kind, minutesOf(kind, state), if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
            } else {
                work.cancelUniqueWork(workName(kind))
            }
        }
    }

    private fun enqueue(app: Context, kind: DailyKind, minutes: Int, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DailyAlertWorker>()
            .setInitialDelay(delayUntil(minutes), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_KIND to kind.name))
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(workName(kind), policy, request)
    }

    suspend fun deliver(context: Context, kind: DailyKind) {
        val app = context.applicationContext
        val state = AlertStore(app).flow.first()
        try {
            if (!isEnabled(kind, state)) return
            val prefs = app.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
            val today = LocalDate.now()
            if (prefs.getLong("last_${kind.name}", -1L) == today.toEpochDay()) return
            val scheduled = today.atTime(minutesOf(kind, state) / 60 % 24, minutesOf(kind, state) % 60).atZone(ZonedDateTime.now().zone)
            val now = ZonedDateTime.now()
            if (now.isBefore(scheduled.minusMinutes(10)) || now.isAfter(scheduled.plusMinutes(GRACE_MINUTES))) return

            val store = SettingsStore(app)
            val settings = store.settings.first()
            I18n.lang = settings.language
            val city = store.currentCity.first() ?: return
            val bundle = freshBundle(app, settings, city) ?: return
            prefs.edit { putLong("last_${kind.name}", today.toEpochDay()) }
            val notice = compose(kind, state, settings, city, bundle) ?: return
            post(app, kind.channel, kind.notificationId, kind.requestCode, notice)
        } finally {
            if (isEnabled(kind, state)) enqueue(app, kind, minutesOf(kind, state), ExistingWorkPolicy.REPLACE)
        }
    }

    private suspend fun freshBundle(app: Context, settings: AppSettings, city: City): WeatherBundle? {
        UserKeys.update(settings.apiKeys)
        if (!NetworkPolicy.foregroundAllowed(app, settings)) return null
        return runCatching { WeatherRepository().fetch(city, settings.weatherSource) }.getOrNull()
    }

    private fun fmt(template: String, vararg args: Any): String =
        String.format(I18n.lang.locale, tr(template), *args)

    private fun temp(celsius: Double, fahrenheit: Boolean): String =
        "${(if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius).roundToInt()}°"

    private fun hourLabel(hour: Int): String = "%02d:00".format(hour)

    private fun clock(time: java.time.LocalDateTime): String =
        time.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun inMonths(mask: Int, date: LocalDate): Boolean = mask and (1 shl (date.monthValue - 1)) != 0

    private suspend fun compose(kind: DailyKind, s: AlertState, settings: AppSettings, city: City, bundle: WeatherBundle): Notice? {
        val base = bundle.current.now.toLocalDate()
        val f = settings.fahrenheit
        return when (kind) {
            DailyKind.Clothing -> {
                val offset = if (s.clothingTime < 14 * 60) 0L else 1L
                val date = base.plusDays(offset)
                val day = bundle.days.firstOrNull { it.date == date } ?: return null
                val hours = bundle.hours.filter { it.time.toLocalDate() == date }
                val advice = ClothingConsultant.advise(day, hours, s.thermal, s.commute, s.workStart, s.workEnd, settings.windUnit, f)
                val summary = fmt("High %1\$s, low %2\$s, %3\$d%% chance of rain", temp(day.max, f), temp(day.min, f), day.rainChance)
                Notice(
                    tr(if (offset == 0L) "What to wear today" else "What to wear tomorrow"),
                    summary,
                    summary + "\n" + advice.items.joinToString("\n") { "${it.section}: ${it.text}" }
                )
            }
            DailyKind.Uv -> {
                if (!inMonths(s.uvMonths, base)) return null
                val day = bundle.days.firstOrNull { it.date == base } ?: return null
                if (day.uvMax < s.uvThreshold) return null
                val peak = bundle.hours.filter { it.time.toLocalDate() == base }.maxByOrNull { it.uv }
                val text = fmt(
                    "UV peaks at %1\$d around %2\$s in %3\$s. Apply sunscreen and seek shade at midday.",
                    day.uvMax.roundToInt(),
                    hourLabel(peak?.time?.hour ?: 12),
                    city.name
                )
                Notice(tr("UV index alerts"), text)
            }
            DailyKind.Pollen, DailyKind.Pill -> {
                val levels = PollenForecast.today(city) ?: return null
                val names = PollenForecast.exceeded(levels, s.pollenSensitivity, s.grasses, s.trees, s.weeds)
                if (names.isEmpty()) return null
                if (kind == DailyKind.Pill) {
                    Notice(tr("Allergy pill reminder"), tr("Pollen is high today. Time to take your allergy medication."))
                } else {
                    Notice(
                        tr("Pollen alerts"),
                        fmt("Pollen levels are elevated today in %1\$s: %2\$s.", city.name, names.joinToString(", ") { tr(it) })
                    )
                }
            }
            DailyKind.Frost -> {
                if (!inMonths(s.frostMonths, base)) return null
                val night = bundle.hours.filter {
                    (it.time.toLocalDate() == base && it.time.hour >= 18) ||
                        (it.time.toLocalDate() == base.plusDays(1) && it.time.hour <= 8)
                }
                val lowest = night.minOfOrNull { it.temp } ?: return null
                if (lowest > 0.0) return null
                Notice(
                    tr("Frost alerts"),
                    fmt("A freezing night is forecast in %1\$s, down to %2\$s. Protect plants and watch for icy roads.", city.name, temp(lowest, f))
                )
            }
            DailyKind.Briefing -> {
                val date = base.plusDays(1)
                val day = bundle.days.firstOrNull { it.date == date } ?: return null
                val short = fmt(
                    "Tomorrow in %1\$s: %2\$s to %3\$s, %4\$d%% chance of rain.",
                    city.name, temp(day.min, f), temp(day.max, f), day.rainChance
                )
                if (!s.briefingDetailed) {
                    Notice(tr("Tomorrow weather briefing"), short)
                } else {
                    val windMax = bundle.hours.filter { it.time.toLocalDate() == date }.maxOfOrNull { it.windSpeed } ?: 0.0
                    val wind = "${(windMax * settings.windUnit.factor).roundToInt()} ${settings.windUnit.label}"
                    val extra = fmt(
                        "Wind up to %1\$s · UV %2\$d · Sunrise %3\$s · Sunset %4\$s",
                        wind, day.uvMax.roundToInt(), clock(day.sunrise), clock(day.sunset)
                    )
                    Notice(tr("Tomorrow weather briefing"), short, short + "\n" + extra)
                }
            }
            DailyKind.Pressure -> {
                val hours = bundle.hours.filter { !it.time.isBefore(bundle.current.now.minusHours(1)) }.take(24)
                var bestDrop = 0.0
                var bestHour = 0
                hours.forEachIndexed { index, hour ->
                    val ahead = hours.drop(index + 1).take(3)
                    val low = ahead.minOfOrNull { it.pressure } ?: return@forEachIndexed
                    val drop = hour.pressure - low
                    if (drop > bestDrop) {
                        bestDrop = drop
                        bestHour = hour.time.hour
                    }
                }
                if (bestDrop < s.pressureDrop) return null
                Notice(
                    tr("Pressure forecast alerts"),
                    fmt(
                        "Pressure is forecast to drop %1\$d hPa within 3 hours around %2\$s. Sensitive people may feel headaches or fatigue.",
                        bestDrop.roundToInt(), hourLabel(bestHour)
                    )
                )
            }
            DailyKind.Aqi -> {
                val hours = bundle.hours.filter { !it.time.isBefore(bundle.current.now.minusHours(1)) && it.aqi != null }.take(18)
                val worst = hours.maxByOrNull { it.aqi ?: 0.0 } ?: return null
                val value = worst.aqi ?: return null
                val level = ((value / 20.0).toInt() + 1).coerceIn(1, 6)
                if (level < s.aqiThreshold) return null
                val name = tr(
                    when (level) {
                        1 -> "Good"
                        2 -> "Fair"
                        3 -> "Moderate"
                        4 -> "Poor"
                        5 -> "Very poor"
                        else -> "Extremely poor"
                    }
                )
                Notice(
                    tr("Air quality alerts"),
                    fmt(
                        "Air quality in %1\$s is expected to reach %2\$s (AQI %3\$d) around %4\$s. Limit outdoor exertion.",
                        city.name, name, value.roundToInt(), hourLabel(worst.time.hour)
                    )
                )
            }
        }
    }

    suspend fun onFresh(context: Context, city: City, bundle: WeatherBundle) {
        val app = context.applicationContext
        reschedule(app, replace = false)
        val state = AlertStore(app).flow.first()
        if (!state.officialOn || bundle.alerts.isEmpty()) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val settings = SettingsStore(app).settings.first()
        I18n.lang = settings.language
        val prefs = app.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
        val seen = (prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()).toMutableSet()
        val nowSeconds = System.currentTimeMillis() / 1000L
        ensureChannels(app)
        bundle.alerts.forEach { alert ->
            val level = levelOf(alert)
            val wanted = when (level) {
                0 -> state.yellowOn
                1 -> state.orangeOn
                else -> state.redOn
            }
            val ends = if (alert.endsEpoch > 100_000_000_000L) alert.endsEpoch / 1000L else alert.endsEpoch
            if (!wanted || (ends in 1 until nowSeconds)) return@forEach
            val key = "${city.id}|${alert.event}|${alert.headline}|${alert.endsEpoch}"
            if (!seen.add(key)) return@forEach
            val headline = alert.headline.ifBlank { alert.event }
            val detail = alert.description.trim().take(600)
            val big = listOf(headline, detail, alert.sender).filter { it.isNotBlank() }.joinToString("\n\n")
            val title = tr(
                when (level) {
                    0 -> "Yellow warning"
                    1 -> "Orange warning"
                    else -> "Red warning"
                }
            )
            post(app, OFFICIAL_CHANNEL, OFFICIAL_ID_BASE + (key.hashCode() and 0x3FF), OFFICIAL_REQUEST, Notice(title, headline, big))
        }
        prefs.edit { putStringSet(KEY_SEEN, seen.toList().takeLast(60).toSet()) }
    }

    private fun levelOf(alert: GovAlert): Int {
        val label = "${alert.severity} ${alert.event} ${alert.headline}".lowercase()
        val words = label.split(Regex("[^a-z]+")).toSet()
        return when {
            "extreme" in words || "red" in words -> 2
            "severe" in words || "orange" in words -> 1
            else -> 0
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(app: Context, channel: String, id: Int, requestCode: Int, notice: Notice) {
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            app, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_stat_rain)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.big))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

class DailyAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = runCatching { DailyKind.valueOf(inputData.getString(DailyAlerts.KEY_KIND).orEmpty()) }.getOrNull()
            ?: return Result.success()
        DailyAlerts.deliver(applicationContext, kind)
        return Result.success()
    }
}
