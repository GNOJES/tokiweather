package com.toki.weather.worker

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.widget.TokiWeatherWidgetLargeReceiver
import com.toki.weather.widget.TokiWeatherWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import java.util.concurrent.TimeUnit

/**
 * WorkManager 스케줄링 관리
 */
object WeatherWorkScheduler {

    private const val PERIODIC_WORK_NAME = "toki_weather_periodic_update"
    private const val ONETIME_WORK_NAME = "toki_weather_onetime_update"
    private const val TAG = "WeatherWorkScheduler"

    private fun coordinator(context: Context) = WidgetWorkCoordinator(
        schedule = { schedule(context, it) },
        cancel = { cancel(context) },
        activeWidgetCounts = {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, TokiWeatherWidgetReceiver::class.java)).size to
                manager.getAppWidgetIds(ComponentName(context, TokiWeatherWidgetLargeReceiver::class.java)).size
        },
        savedIntervalMinutes = { WeatherDataStore(context).updateIntervalFlow.first().toLong() },
        hasActiveSchedule = {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(PERIODIC_WORK_NAME).get()
                .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
        }
    )

    fun onWidgetEnabled(context: Context, pending: BroadcastReceiver.PendingResult) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                coordinator(context).onWidgetEnabled()
                runOnce(context)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to restore widget update interval: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    fun onWidgetDisabled(context: Context) {
        coordinator(context).onWidgetDisabled()
    }

    fun onWidgetUpdated(context: Context, pending: BroadcastReceiver.PendingResult) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }
    }

    suspend fun ensureScheduled(context: Context) {
        try {
            coordinator(context).onWidgetUpdated()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to restore existing widget schedule: ${e.javaClass.simpleName}")
        }
    }

    /**
     * 주기적 업데이트 등록 (기본값: 30분, WorkManager 최소 간격: 15분)
     */
    fun schedule(context: Context, intervalMinutes: Long = 30L) {
        val safeMinutes = intervalMinutes.coerceAtLeast(15L)
        Log.d(TAG, "Scheduling periodic weather update every $safeMinutes minutes")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            safeMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(PERIODIC_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * 즉시 1회 업데이트 실행
     */
    fun runOnce(context: Context) {
        Log.d(TAG, "Running one-time weather update")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONETIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * 주기적 업데이트 취소
     */
    fun cancel(context: Context) {
        Log.d(TAG, "Cancelling periodic weather update")
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
