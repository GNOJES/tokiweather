package com.toki.weather.data.repository

import com.toki.weather.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

class ShortForecastFallbackTest {
    private val now = LocalDateTime.of(2026, 10, 15, 13, 43)
    private fun item(issue: String) = KmaResponse.Item("20261015", issue, "SKY", "20261015", "1300", "3", null, 58, 127)
    private fun noData(): Nothing {
        requireKmaItems(KmaResponse(KmaResponse.Response(KmaResponse.Header("03", "unused"), null)))
        error("Expected no-data exception")
    }

    @Test fun latestSuccessDoesNotFetchPrevious() = runBlocking {
        var calls = 0
        val result = fetchShortForecastWithFallback(now) { date, time, previous ->
            calls++
            assertEquals("20261015", date); assertEquals("1100", time); assertFalse(previous)
            listOf(item(time))
        }
        assertEquals(1, calls); assertFalse(result.usedPreviousIssue)
    }

    @Test fun noDataUsesOnlyTheImmediatelyPreviousIssueAndKeepsActualIssue() = runBlocking {
        val calls = mutableListOf<String>()
        val result = fetchShortForecastWithFallback(now) { _, time, previous ->
            calls += time
            if (!previous) noData()
            listOf(item(time))
        }
        assertEquals(listOf("1100", "0800"), calls)
        assertTrue(result.usedPreviousIssue); assertEquals("0800", result.items.single().baseTime)
    }

    @Test fun handlesPreviousDateAtFirstMorningIssue() = runBlocking {
        val result = fetchShortForecastWithFallback(LocalDateTime.of(2027, 1, 1, 2, 20)) { date, time, previous ->
            if (!previous) noData()
            assertEquals("20261231", date); assertEquals("2300", time)
            listOf(item(time))
        }
        assertTrue(result.usedPreviousIssue)
    }

    @Test fun bothIssuesMissingFailsWithoutSearchingFurtherBack() {
        var calls = 0
        assertThrows(KmaNoDataException::class.java) { runBlocking {
            fetchShortForecastWithFallback(now) { _, _, _ -> calls++; noData() }
        } }
        assertEquals(2, calls)
    }

    @Test fun authenticationNetworkAndCancellationDoNotTriggerFallback() {
        for (failure in listOf(IllegalStateException("fixed"), IOException(), CancellationException())) {
            var calls = 0
            try {
                runBlocking { fetchShortForecastWithFallback(now) { _, _, _ -> calls++; throw failure } }
                fail("Expected failure")
            } catch (actual: Exception) { assertSame(failure, actual) }
            assertEquals(1, calls)
        }
    }

    @Test fun missingLaterPageDiscardsAllPartialDataBeforeFallback() = runBlocking {
        val result = fetchShortForecastWithFallback(now) { _, time, previous ->
            if (previous) listOf(item(time)) else fetchAllKmaItems { page ->
                if (page == 2) noData()
                KmaResponse(KmaResponse.Response(KmaResponse.Header("00", "unused"),
                    KmaResponse.Body(KmaResponse.Items(listOf(item(time))), 2)))
            }
        }
        assertEquals(listOf(item("0800")), result.items)
    }
}
