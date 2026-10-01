package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import com.toki.weather.util.GridConverter
import com.toki.weather.util.LocationInfo
import kotlinx.coroutines.CancellationException

/** 저장된 실제 조회 좌표만 사용하며, 빈 캐시와 과거 기본 서울 위치는 재사용하지 않는다. */
fun savedWeatherLocation(weather: CachedWeather?): LocationInfo? {
    if (weather == null || weather.lastUpdated <= 0 || weather.locationName == "설정 위치") return null
    val latitude = weather.airQualityLatitude ?: return null
    val longitude = weather.airQualityLongitude ?: return null
    if (!latitude.isFinite() || !longitude.isFinite() ||
        latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    val grid = GridConverter.toGrid(latitude, longitude)
    return LocationInfo(grid.nx, grid.ny, weather.locationName, latitude, longitude)
}

suspend fun resolveWeatherRefreshLocation(
    allowSavedLocation: Boolean,
    currentLocation: suspend () -> LocationInfo,
    savedLocation: suspend () -> LocationInfo?
): LocationInfo = try {
    currentLocation()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    if (!allowSavedLocation) throw e
    savedLocation() ?: throw e
}
