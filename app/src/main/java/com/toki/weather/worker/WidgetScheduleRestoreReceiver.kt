package com.toki.weather.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.toki.weather.widget.TokiWeatherWidget
import com.toki.weather.widget.TokiWeatherWidgetLarge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restores periodic work for widgets already installed when the APK is replaced. */
class WidgetScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    WeatherWorkScheduler.ensureScheduled(context)
                    // APK 교체 전 렌더링된 '설정 위치' 위젯도 정리된 캐시로 다시 그린다.
                    TokiWeatherWidget().updateAll(context)
                    TokiWeatherWidgetLarge().updateAll(context)
                } catch (e: Exception) {
                    Log.w("WidgetScheduleRestore", "Widget redraw failed: ${e.javaClass.simpleName}")
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
