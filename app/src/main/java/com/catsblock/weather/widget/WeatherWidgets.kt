package com.catsblock.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.catsblock.weather.work.BackgroundUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseWeatherWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAsync(context, appWidgetManager, appWidgetIds)
        val snapshot = WidgetStore.load(context)
        if (snapshot == null || System.currentTimeMillis() - snapshot.savedAt > STALE_MILLIS) {
            BackgroundUpdates.runNow(context)
        }
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
        BackgroundUpdates.schedule(context)
        BackgroundUpdates.runNow(context)
    }

    private fun renderAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.render(app, manager, kind, ids)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val STALE_MILLIS = 3 * 60 * 60 * 1000L
    }
}

class CompactWeatherWidget : BaseWeatherWidget(WidgetKind.Compact)

class SquareWeatherWidget : BaseWeatherWidget(WidgetKind.Square)

class TipsWeatherWidget : BaseWeatherWidget(WidgetKind.Tips)

class HourlyWeatherWidget : BaseWeatherWidget(WidgetKind.Hourly)
