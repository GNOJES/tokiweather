package com.toki.weather.worker

import com.toki.weather.data.model.CachedWeather
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SettingsRefreshTest {
    @Test fun savedSettingsMustReachCachedWidgetsEvenWhenWeatherFails() = runBlocking {
        var renders = 0
        var saved = false
        val outcome = saveSettingsAndRefresh(save = { saved = true },
            fetch = { Result.failure(IOException()) }, render = { assertTrue(saved); renders++ })
        assertNotNull(outcome.weatherFailure)
        assertNull(outcome.displayFailure)
        assertEquals(1, renders)
    }
    @Test fun saveFailureDoesNotFetchOrDisplayUnsavedSettings() = runBlocking {
        try {
            saveSettingsAndRefresh(save = { throw IOException() },
                fetch = { fail("Save failed"); Result.success(CachedWeather.EMPTY) },
                render = { fail("Save failed") })
            fail("Save failure must propagate")
        } catch (_: IOException) { }
    }
    @Test fun displayFailureStillAllowsWeatherAndSuccessfulFinalDisplay() = runBlocking {
        var calls = 0
        val outcome = saveSettingsAndRefresh(save = {}, fetch = { Result.success(CachedWeather.EMPTY) },
            render = { if (++calls == 1) throw IOException() })
        assertEquals(2, calls)
        assertNull(outcome.weatherFailure)
        assertNull(outcome.displayFailure)
    }

}
