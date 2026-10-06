package com.toki.weather.worker

import com.toki.weather.data.model.CachedWeather
import kotlinx.coroutines.CancellationException

suspend fun refreshAndUpdate(
    fetch: suspend () -> Result<CachedWeather>,
    updateWidgets: suspend () -> Unit
): CachedWeather {
    val weather = fetch().getOrThrow()
    try { updateWidgets() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { throw WidgetDisplayException() }
    return weather
}
