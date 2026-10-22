package com.catsblock.weather.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.catsblock.weather.alerts.RainAlerts
import com.catsblock.weather.data.NetworkPolicy
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.data.WeatherBus
import com.catsblock.weather.data.WeatherRepository
import com.catsblock.weather.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object BackgroundUpdates {

    const val KEY_MANUAL = "manual"
    private const val PERIODIC_NAME = "weather_periodic_update"
    private const val NOW_NAME = "weather_update_now"

    private val connected: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(2, TimeUnit.HOURS)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setInputData(workDataOf(KEY_MANUAL to true))
            .setConstraints(connected)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val store = SettingsStore(app)
        val settings = store.settings.first()
        if (!NetworkPolicy.foregroundAllowed(app, settings)) return Result.success()
        val city = store.currentCity.first() ?: return Result.success()
        return try {
            val bundle = WeatherRepository().fetch(city)
            WidgetUpdater.publish(app, city, bundle)
            runCatching { RainAlerts.onFresh(app, city, bundle) }
            WeatherBus.emit(city, bundle)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
