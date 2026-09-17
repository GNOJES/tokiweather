package com.toki.weather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.toki.weather.worker.WeatherWorkScheduler

/**
 * 3×2 Large 위젯 리시버 (노바런처 등 큰 그리드 전용)
 * 동일한 TokiWeatherWidget을 재사용하며, SizeMode.Exact로 런타임에 크기 감지 후
 * 세로형 레이아웃(isLarge)을 자동 적용
 */
class TokiWeatherWidgetLargeReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TokiWeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorkScheduler.schedule(context)
        WeatherWorkScheduler.runOnce(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherWorkScheduler.cancel(context)
    }
}
