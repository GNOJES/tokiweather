package com.toki.weather.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager 스케줄링 관리
 */
object WeatherWorkScheduler {

    private const val PERIODIC_WORK_NAME = "toki_weather_periodic_update"
    private const val ONETIME_WORK_NAME = "toki_weather_onetime_update"
    private const val TAG = "WeatherWorkScheduler"

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
