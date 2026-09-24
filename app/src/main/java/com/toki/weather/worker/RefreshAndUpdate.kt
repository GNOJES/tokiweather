package com.toki.weather.worker

import com.toki.weather.data.model.CachedWeather

suspend fun refreshAndUpdate(
    fetch: suspend () -> Result<CachedWeather>,
    updateWidgets: suspend () -> Unit
): CachedWeather {
    val weather = fetch().getOrThrow()
    updateWidgets()
    return weather
}
