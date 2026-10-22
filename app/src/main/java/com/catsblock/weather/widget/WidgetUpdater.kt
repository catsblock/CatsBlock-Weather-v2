package com.catsblock.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.catsblock.weather.MainActivity
import com.catsblock.weather.R
import com.catsblock.weather.data.City
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.i18n.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object WidgetUpdater {

    private val providers: Map<WidgetKind, Class<*>> = mapOf(
        WidgetKind.Compact to CompactWeatherWidget::class.java,
        WidgetKind.Square to SquareWeatherWidget::class.java,
        WidgetKind.Tips to TipsWeatherWidget::class.java,
        WidgetKind.Hourly to HourlyWeatherWidget::class.java
    )

    suspend fun publish(context: Context, city: City, bundle: WeatherBundle) {
        val app = context.applicationContext
        val snapshot = WidgetSnapshot.from(city, bundle)
        WidgetStore.save(app, snapshot)
        val icons = listOf(snapshot.kind to snapshot.isDay) +
            snapshot.upcomingHours(24).map { it.kind to it.isDay }
        WidgetIcons.prefetch(app, icons)
        refresh(app)
    }

    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        providers.forEach { (kind, provider) ->
            val ids = manager.getAppWidgetIds(ComponentName(app, provider))
            if (ids.isNotEmpty()) render(app, manager, kind, ids)
        }
    }

    suspend fun render(context: Context, manager: AppWidgetManager, kind: WidgetKind, ids: IntArray) {
        withContext(Dispatchers.IO) {
            val settings = SettingsStore(context).settings.first()
            I18n.lang = settings.language
            val snapshot = WidgetStore.load(context)
            val click = openApp(context)
            ids.forEach { id ->
                val (width, height) = sizeOf(context, manager, id, kind)
                val bitmap = WidgetRenderer.render(context, kind, width, height, snapshot, settings)
                val views = RemoteViews(context.packageName, R.layout.widget_image)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setOnClickPendingIntent(R.id.widget_image, click)
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun sizeOf(context: Context, manager: AppWidgetManager, id: Int, kind: WidgetKind): Pair<Int, Int> {
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
        val fallback = WidgetRenderer.defaultSize(kind)
        return (if (width > 0) width else fallback.first) to (if (height > 0) height else fallback.second)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
