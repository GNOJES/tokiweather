package com.toki.weather.data.model

import org.junit.Assert.*
import org.junit.Test

class RefreshPresentationTest {
    private val weather = CachedWeather.EMPTY.copy(lastUpdated = 1_000_000,
        refresh = RefreshMetadata(requestId = 2))
    @Test fun newerFailedAttemptMarksPreviousDataButDisplayFailureDoesNot() {
        val event = RefreshEvent(3, RefreshSource.AUTOMATIC, RefreshStage.REQUEST, RefreshState.FAILED, at = 1_100_000)
        assertTrue(weather.usesPreviousWeather(1_200_000, 30, listOf(event)))
        assertFalse(weather.usesPreviousWeather(1_200_000, 30, listOf(event.copy(source = RefreshSource.DISPLAY))))
        assertFalse(weather.usesPreviousWeather(1_200_000, 30, listOf(event.copy(requestId = 1))))
    }
    @Test fun ageMarksPreviousDataEvenWithoutRecentDiagnosticEvents() {
        assertTrue(weather.usesPreviousWeather(3_000_000, 30, emptyList()))
        assertFalse(weather.usesPreviousWeather(1_200_000, 30, emptyList()))
        assertFalse(CachedWeather.EMPTY.usesPreviousWeather(3_000_000, 30, emptyList()))
    }
}
