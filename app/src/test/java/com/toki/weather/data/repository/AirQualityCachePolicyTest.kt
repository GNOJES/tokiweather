package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class AirQualityCachePolicyTest {
    private val now = 1_000_000_000L

    @Test fun requestTimeoutKeepsPreviouslyObservedValuesWithinThreeHours() {
        val previous = weather(pm10 = 20, pm25 = 16, observedAt = now - 30 * 60_000L)
        val result = mergeAirQuality(weather(), AirQualityReading(-1, -1, null, failedToLoad = true), previous, now)
        assertEquals(20, result.pm10)
        assertEquals(16, result.pm25)
        assertEquals(previous.pmObservedAt, result.pmObservedAt)
    }

    @Test fun timeoutDoesNotReuseExpiredMeasurement() {
        val previous = weather(pm10 = 20, pm25 = 16, observedAt = now - 4 * 60 * 60_000L)
        val result = mergeAirQuality(weather(), AirQualityReading(-1, -1, null, failedToLoad = true), previous, now)
        assertEquals(-1, result.pm10)
        assertEquals(-1, result.pm25)
    }

    @Test fun flaggedMeasurementDoesNotReuseOldValue() {
        val previous = weather(pm10 = 20, pm25 = 16, observedAt = now - 30 * 60_000L)
        val result = mergeAirQuality(weather(), AirQualityReading(-1, -1, now, failedToLoad = false), previous, now)
        assertEquals(-1, result.pm10)
        assertEquals(-1, result.pm25)
    }

    @Test fun timeoutDoesNotReuseValuesFromAnotherLocation() {
        val previous = weather(pm10 = 20, pm25 = 16, observedAt = now - 30 * 60_000L)
            .copy(locationName = "같은 이름", airQualityLatitude = 37.56, airQualityLongitude = 126.97)
        val result = mergeAirQuality(weather().copy(locationName = "현재 위치"),
            AirQualityReading(-1, -1, null, failedToLoad = true), previous, now)
        assertEquals(-1, result.pm10)
        assertEquals(-1, result.pm25)
    }

    @Test fun timeoutKeepsNearbyReadingEvenWhenAddressLabelChanges() {
        val previous = weather(pm10 = 20, pm25 = 16, observedAt = now - 30 * 60_000L)
            .copy(locationName = "영등포동7가", airQualityLatitude = 37.52, airQualityLongitude = 126.91)
        val current = weather().copy(locationName = "영등포구", airQualityLatitude = 37.521, airQualityLongitude = 126.911)
        val result = mergeAirQuality(current, AirQualityReading(-1, -1, null, failedToLoad = true), previous, now)
        assertEquals(20, result.pm10)
        assertEquals(16, result.pm25)
    }

    private fun weather(pm10: Int = -1, pm25: Int = -1, observedAt: Long? = null) = CachedWeather(
        currentTemp = 20,
        currentCondition = WeatherCondition.CLEAR,
        pm10 = pm10,
        pm25 = pm25,
        pmObservedAt = observedAt,
        airQualityLatitude = 37.52,
        airQualityLongitude = 126.91,
        tomorrowMin = null,
        tomorrowMax = null,
        tomorrowCondition = WeatherCondition.UNKNOWN,
        dayAfterMin = null,
        dayAfterMax = null,
        dayAfterCondition = WeatherCondition.UNKNOWN
    )
}
