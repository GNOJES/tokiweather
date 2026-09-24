package com.toki.weather.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.toki.weather.worker.WeatherWorkScheduler

/**
 * 위젯 리시버
 * 위젯 추가/삭제 시 WorkManager 스케줄링 관리
 */
class TokiWeatherWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TokiWeatherWidget()

    /**
     * 첫 번째 위젯이 홈 화면에 추가될 때
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // WorkManager 주기적 업데이트 시작
        WeatherWorkScheduler.onWidgetEnabled(context, goAsync())
    }

    /**
     * 마지막 위젯이 홈 화면에서 제거될 때
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // WorkManager 중지
        WeatherWorkScheduler.onWidgetDisabled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherWorkScheduler.onWidgetUpdated(context, goAsync())
    }
}
