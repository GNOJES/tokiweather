package com.toki.weather.worker

import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WeatherCondition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RefreshAndUpdateTest {
    private val sample = CachedWeather(
        currentTemp = 17, currentCondition = WeatherCondition.CLEAR,
        tomorrowMin = 10, tomorrowMax = 20, tomorrowCondition = WeatherCondition.CLOUDY,
        dayAfterMin = 11, dayAfterMax = 21, dayAfterCondition = WeatherCondition.RAIN
    )

    @Test fun failedFetchNeverUpdatesWidgetsOrSignalsSuccess() = runBlocking {
        var widgetUpdates = 0
        try {
            refreshAndUpdate(
                fetch = { Result.failure(IOException("offline")) },
                updateWidgets = { widgetUpdates++ }
            )
            fail("Fetch failure must be thrown")
        } catch (_: IOException) {
            assertEquals(0, widgetUpdates)
        }
    }

    @Test fun successfulFetchUpdatesWidgetsOnce() = runBlocking {
        var widgetUpdates = 0
        val actual = refreshAndUpdate(
            fetch = { Result.success(sample) },
            updateWidgets = { widgetUpdates++ }
        )
        assertEquals(sample, actual)
        assertEquals(1, widgetUpdates)
    }

    @Test fun cancellationIsNotConvertedToNormalFailure() = runBlocking {
        try {
            refreshAndUpdate(
                fetch = { throw CancellationException("stopped") },
                updateWidgets = { fail("Widgets should not update") }
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
    @Test fun displayFailureIsDistinctFromWeatherFetchFailure() = runBlocking {
        var fetches = 0
        try {
            refreshAndUpdate(fetch = { fetches++; Result.success(sample) },
                updateWidgets = { throw IOException("private URL must not escape in display error") })
            fail("Display failure must be distinct")
        } catch (e: WidgetDisplayException) {
            assertEquals(1, fetches)
            assertFalse(e.message.orEmpty().contains("private URL"))
        }
    }

}
