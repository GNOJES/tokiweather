package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 요청 실패 시에만 관측 시각이 유효한 기존 실측값을 유지한다. */
fun mergeAirQuality(
    forecast: CachedWeather,
    reading: AirQualityReading,
    previous: CachedWeather?,
    now: Long
): CachedWeather {
    val canRetain = reading.failedToLoad && previous != null &&
        nearby(previous.airQualityLatitude, previous.airQualityLongitude,
            forecast.airQualityLatitude, forecast.airQualityLongitude) &&
        previous.pmObservedAt != null && now - previous.pmObservedAt in 0L..3 * 60 * 60 * 1000L
    return if (canRetain) {
        forecast.copy(
            pm10 = previous!!.pm10,
            pm25 = previous.pm25,
            pmObservedAt = previous.pmObservedAt,
            airQualityLatitude = previous.airQualityLatitude,
            airQualityLongitude = previous.airQualityLongitude
        )
    } else {
        forecast.copy(pm10 = reading.pm10, pm25 = reading.pm25, pmObservedAt = reading.observedAt)
    }
}

private fun nearby(lat1: Double?, lon1: Double?, lat2: Double?, lon2: Double?): Boolean {
    if (lat1 == null || lon1 == null || lat2 == null || lon2 == null ||
        !lat1.isFinite() || !lon1.isFinite() || !lat2.isFinite() || !lon2.isFinite()) return false
    val deltaLat = Math.toRadians(lat2 - lat1)
    val deltaLon = Math.toRadians(lon2 - lon1)
    val a = sin(deltaLat / 2).pow(2) + cos(Math.toRadians(lat1)) *
        cos(Math.toRadians(lat2)) * sin(deltaLon / 2).pow(2)
    return 6371.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0))) <= 2.0
}
