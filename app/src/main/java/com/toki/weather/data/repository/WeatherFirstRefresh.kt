package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import kotlinx.coroutines.withTimeoutOrNull

/** The first commit is visible before any air request starts. Both commits check request ownership. */
suspend fun publishWeatherThenAir(
    weather: CachedWeather,
    publish: suspend (CachedWeather) -> Boolean,
    onWeatherPublished: suspend () -> Unit = {},
    fetchAir: suspend () -> AirQualityReading,
    finishAir: suspend (AirQualityReading) -> Unit,
    airTimeoutMs: Long = 8_000L
): Boolean {
    if (!publish(weather)) return false
    onWeatherPublished()
    val reading = withTimeoutOrNull(airTimeoutMs) { fetchAir() }
        ?: AirQualityReading(-1, -1, null, failedToLoad = true, timedOut = true)
    finishAir(reading)
    return true
}
