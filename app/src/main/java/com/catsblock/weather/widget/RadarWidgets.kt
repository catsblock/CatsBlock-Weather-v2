package com.catsblock.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.catsblock.weather.MainActivity
import com.catsblock.weather.R
import com.catsblock.weather.data.GovAlertStore
import com.catsblock.weather.data.MapStyle
import com.catsblock.weather.data.NetworkPolicy
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

abstract class BaseRadarWidget(private val wide: Boolean) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        renderAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        RadarWidgetWork.schedule(context)
    }

    override fun onDisabled(context: Context) {
        if (!RadarWidgetUpdater.hasWidgets(context)) RadarWidgetWork.cancel(context)
    }

    private fun renderAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RadarWidgetUpdater.render(app, manager, ids, wide)
            } finally {
                pending.finish()
            }
        }
    }
}

class RadarSmallWidget : BaseRadarWidget(false)

class RadarWideWidget : BaseRadarWidget(true)

object RadarWidgetUpdater {

    private val providers: List<Pair<Class<*>, Boolean>> = listOf(
        RadarSmallWidget::class.java to false,
        RadarWideWidget::class.java to true
    )

    fun hasWidgets(context: Context): Boolean {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        return providers.any { (provider, _) -> manager.getAppWidgetIds(ComponentName(app, provider)).isNotEmpty() }
    }

    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        providers.forEach { (provider, wide) ->
            val ids = manager.getAppWidgetIds(ComponentName(app, provider))
            if (ids.isNotEmpty()) render(app, manager, ids, wide)
        }
    }

    suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray, wide: Boolean) {
        withContext(Dispatchers.IO) {
            val store = SettingsStore(context)
            val settings = store.settings.first()
            I18n.lang = settings.language
            val dark = MapStyle.isDark(context, settings.theme)
            val city = store.currentCity.first()
            val density = context.resources.displayMetrics.density
            val click = openApp(context)
            ids.forEach { id ->
                val (widthDp, heightDp) = sizeOf(context, manager, id, wide)
                val widthPx = (widthDp * density).roundToInt()
                val heightPx = (heightDp * density).roundToInt()
                val bitmap = if (city == null) {
                    RadarWidgetRenderer.placeholder(widthPx, heightPx, tr("Open the app to choose a location"), density, dark)
                } else {
                    RadarWidgetRenderer.render(context, city, widthPx, heightPx, GovAlertStore.current(context, city), dark)
                }
                if (bitmap != null) {
                    val views = RemoteViews(context.packageName, R.layout.widget_image)
                    views.setImageViewBitmap(R.id.widget_image, bitmap)
                    views.setOnClickPendingIntent(R.id.widget_image, click)
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }

    private fun sizeOf(context: Context, manager: AppWidgetManager, id: Int, wide: Boolean): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(id)
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            0
        )
        val height = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            0
        )
        val fallbackWidth = if (wide) 300 else 150
        return (if (width > 0) width else fallbackWidth) to (if (height > 0) height else 150)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

object RadarWidgetWork {

    private const val NAME = "radar_widget_update"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RadarWidgetWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
    }
}

class RadarWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val settings = SettingsStore(app).settings.first()
        if (!NetworkPolicy.foregroundAllowed(app, settings)) return Result.success()
        return try {
            RadarWidgetUpdater.refresh(app)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
