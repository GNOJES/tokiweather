package com.toki.weather.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.*
import com.toki.weather.widget.TokiWeatherWidget
import com.toki.weather.widget.TokiWeatherWidgetLarge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class WidgetDisplayException : Exception("Weather saved; widget display request failed")

/** Requests rendering of the existing cache only. A retry never fetches weather. */
suspend fun requestWeatherWidgetDisplay(context: Context, scheduleRetry: Boolean = true) {
    val store = WeatherDataStore(context)
    val id = store.weatherFlow.first().refresh.requestId
    val start = System.nanoTime()
    var failed = false
    store.record(RefreshEvent(id, RefreshSource.DISPLAY, RefreshStage.WIDGET, RefreshState.STARTED))
    for (render in listOf<suspend () -> Unit>(
        { TokiWeatherWidget().updateAll(context) },
        { TokiWeatherWidgetLarge().updateAll(context) }
    )) {
        try {
            if (withTimeoutOrNull(2_000L) { render(); true } != true) failed = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    store.record(RefreshEvent(id, RefreshSource.DISPLAY, RefreshStage.WIDGET,
        if (failed) RefreshState.FAILED else RefreshState.REQUESTED,
        durationMs = (System.nanoTime() - start) / 1_000_000))
    if (failed) {
        if (scheduleRetry) WorkManager.getInstance(context).enqueueUniqueWork(
            "toki_widget_display_retry", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WidgetDisplayWorker>().build())
        throw WidgetDisplayException()
    }
}

class WidgetDisplayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        requestWeatherWidgetDisplay(applicationContext, scheduleRetry = false)
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}
