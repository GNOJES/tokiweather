package com.toki.weather.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.toki.weather.worker.WeatherWorkScheduler

/**
 * 3×2 Large 위젯 리시버 (노바런처 등 큰 그리드 전용)
 * 동일한 TokiWeatherWidget을 재사용하며, SizeMode.Exact로 런타임에 크기 감지 후
 * 세로형 레이아웃(isLarge)을 자동 적용
 */
class TokiWeatherWidgetLargeReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TokiWeatherWidgetLarge()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorkScheduler.onWidgetEnabled(context, goAsync())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherWorkScheduler.onWidgetDisabled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherWorkScheduler.onWidgetUpdated(context, goAsync())
    }
}
