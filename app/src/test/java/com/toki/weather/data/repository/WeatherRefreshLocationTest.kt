package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import com.toki.weather.util.GridConverter
import com.toki.weather.util.LocationInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WeatherRefreshLocationTest {
    private val saved = CachedWeather.EMPTY.copy(
        locationName = "상암동", lastUpdated = 1,
        airQualityLatitude = 37.58, airQualityLongitude = 126.89
    )
    private val fresh = LocationInfo(60, 127, "새 지역", 37.6, 127.0)

    @Test fun freshLocationAlwaysWinsAfterMoving() = runBlocking {
        assertEquals(fresh, resolveWeatherRefreshLocation(true,
            { fresh }, { fail("Do not use old location when fresh GPS works"); null }))
    }

    @Test fun backgroundFailureUsesLastConfirmedRegion() = runBlocking {
        val actual = resolveWeatherRefreshLocation(true,
            { throw IllegalStateException() }, { savedWeatherLocation(saved) })
        assertEquals("상암동", actual.locationName)
        assertEquals(saved.airQualityLatitude, actual.latitude)
        assertEquals(saved.airQualityLongitude, actual.longitude)
        val grid = GridConverter.toGrid(37.58, 126.89)
        assertEquals(grid.nx, actual.nx)
        assertEquals(grid.ny, actual.ny)
    }

    @Test fun manualRefreshDoesNotSilentlyUseOldRegion() = runBlocking {
        val error = IllegalStateException()
        try {
            resolveWeatherRefreshLocation(false, { throw error },
                { fail("Manual refresh must require fresh location"); null })
            fail("Manual refresh must fail")
        } catch (actual: IllegalStateException) { assertSame(error, actual) }
    }

    @Test fun missingSavedCoordinatesNeverSubstituteSeoul() = runBlocking {
        val error = IllegalStateException()
        try {
            resolveWeatherRefreshLocation(true, { throw error },
                { savedWeatherLocation(saved.copy(airQualityLatitude = null)) })
            fail("Missing actual coordinates must fail")
        } catch (actual: IllegalStateException) { assertSame(error, actual) }
    }

    @Test fun cancellationDoesNotTriggerLocationFallback() = runBlocking {
        try {
            resolveWeatherRefreshLocation(true, { throw CancellationException() },
                { fail("Cancellation must stop worker"); null })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun invalidAndLegacyCacheCannotProvideLocation() {
        assertNull(savedWeatherLocation(null))
        assertNull(savedWeatherLocation(CachedWeather.EMPTY))
        assertNull(savedWeatherLocation(saved.copy(lastUpdated = 0)))
        assertNull(savedWeatherLocation(saved.copy(locationName = "설정 위치")))
        assertNull(savedWeatherLocation(saved.copy(airQualityLatitude = Double.NaN)))
        assertNull(savedWeatherLocation(saved.copy(airQualityLongitude = 181.0)))
    }
}
