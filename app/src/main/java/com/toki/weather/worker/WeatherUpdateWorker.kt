package com.toki.weather.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.toki.weather.data.repository.WeatherRepository
import com.toki.weather.widget.TokiWeatherWidget
import com.toki.weather.widget.TokiWeatherWidgetLarge

/**
 * 백그라운드 날씨 데이터 업데이트 Worker
 * WorkManager에 의해 주기적으로 실행
 */
class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "WeatherUpdateWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Weather update started")

        val repo = WeatherRepository(applicationContext)
        val outcome = backgroundRefreshAndUpdate(
            fetch = { repo.fetchAndSave(allowSavedLocation = true,
                onWeatherSaved = { requestWeatherWidgetDisplay(applicationContext) }) },
            updateWidgets = listOf({ requestWeatherWidgetDisplay(applicationContext) })
        )
        outcome.fetchFailure?.let {
            Log.e(TAG, "Weather fetch failed: ${it.javaClass.simpleName}")
        }
        outcome.widgetFailures.forEach {
            Log.e(TAG, "Failed to update widget: ${it.javaClass.simpleName}")
        }
        return if (outcome.weatherSaved) {
            Log.d(TAG, "Weather refresh finished; display requests handled separately")
            Result.success()
        } else {
            Result.retry()
        }
    }
}
