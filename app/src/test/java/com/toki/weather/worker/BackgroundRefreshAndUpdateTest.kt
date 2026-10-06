package com.toki.weather.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BackgroundRefreshAndUpdateTest {
    @Test fun failedFetchRedrawsBothCachedWidgetsWithoutReportingSuccess() = runBlocking {
        val error = IOException()
        var updates = 0
        val outcome = backgroundRefreshAndUpdate<Unit>(
            fetch = { Result.failure(error) },
            updateWidgets = listOf({ updates++ }, { updates++ })
        )
        assertEquals(2, updates)
        assertSame(error, outcome.fetchFailure)
        assertFalse(outcome.succeeded)
    }

    @Test fun failedStandardWidgetDoesNotPreventLargeWidgetUpdate() = runBlocking {
        val error = IOException()
        var largeUpdated = false
        val outcome = backgroundRefreshAndUpdate(
            fetch = { Result.success(Unit) },
            updateWidgets = listOf({ throw error }, { largeUpdated = true })
        )
        assertTrue(largeUpdated)
        assertNull(outcome.fetchFailure)
        assertEquals(listOf(error), outcome.widgetFailures)
        assertFalse(outcome.succeeded)
        assertTrue(outcome.weatherSaved) // display retry must not repeat the API fetch
    }

    @Test fun successfulFetchIsSavedBeforeWidgetsAreRedrawn() = runBlocking {
        var saved = false
        val outcome = backgroundRefreshAndUpdate(
            fetch = { saved = true; Result.success(Unit) },
            updateWidgets = listOf({ assertTrue(saved) }, { assertTrue(saved) })
        )
        assertTrue(outcome.succeeded)
    }

    @Test fun unexpectedFetchExceptionStillRedrawsWidgets() = runBlocking {
        var updates = 0
        val error = IllegalStateException()
        val outcome = backgroundRefreshAndUpdate<Unit>(
            fetch = { throw error },
            updateWidgets = listOf({ updates++ }, { updates++ })
        )
        assertEquals(2, updates)
        assertSame(error, outcome.fetchFailure)
    }

    @Test fun cancelledFetchDoesNotStartWidgetUpdates() = runBlocking {
        try {
            backgroundRefreshAndUpdate<Unit>(
                fetch = { throw CancellationException() },
                updateWidgets = listOf({ fail("Cancelled worker must stop") })
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun cancelledWidgetUpdateStopsRemainingWork() = runBlocking {
        try {
            backgroundRefreshAndUpdate(
                fetch = { Result.success(Unit) },
                updateWidgets = listOf(
                    { throw CancellationException() },
                    { fail("Cancelled worker must stop") }
                )
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
