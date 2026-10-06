package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WeatherFirstRefreshTest {
    @Test fun slowAirDoesNotDelayPublishedWeatherAndTimeoutPreservesFailureMeaning() = runBlocking {
        val published = CompletableDeferred<Unit>()
        val airStarted = CompletableDeferred<Unit>()
        var weatherVisible = false
        var widgetRequested = false
        var reading: AirQualityReading? = null
        val job = launch {
            publishWeatherThenAir(CachedWeather.EMPTY,
                publish = { weatherVisible = true; published.complete(Unit); true },
                onWeatherPublished = { widgetRequested = true },
                fetchAir = { airStarted.complete(Unit); delay(5_000); AirQualityReading(20, 10, 100) },
                finishAir = { reading = it }, airTimeoutMs = 30)
        }
        published.await(); airStarted.await()
        assertTrue(weatherVisible)
        assertTrue(widgetRequested)
        assertNull(reading)
        job.join()
        assertTrue(reading!!.failedToLoad)
        assertEquals(-1, reading!!.pm10)
    }
    @Test fun supersededRequestNeverStartsAirOrRequestsWidgetDisplay() = runBlocking {
        val accepted = publishWeatherThenAir(CachedWeather.EMPTY, publish = { false },
            onWeatherPublished = { fail("Superseded request must stop") },
            fetchAir = { fail("Superseded request must stop"); AirQualityReading(1, 1, 1) },
            finishAir = { fail("Superseded request must stop") })
        assertFalse(accepted)
    }
}
