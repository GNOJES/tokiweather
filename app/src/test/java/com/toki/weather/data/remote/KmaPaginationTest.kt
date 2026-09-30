package com.toki.weather.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KmaPaginationTest {
    private fun item(category: String, time: String) = KmaResponse.Item(
        "20260930", "1700", category, "20261004", time, "0", null, 58, 127
    )

    private fun page(items: List<KmaResponse.Item>, total: Int, code: String = "00") =
        KmaResponse(KmaResponse.Response(
            KmaResponse.Header(code, "message"),
            KmaResponse.Body(KmaResponse.Items(items), total)
        ))

    @Test fun collectsRemainingForecastFieldsAndHoursFromLaterPages() = runBlocking {
        val first = listOf(item("TMP", "1200"), item("POP", "1200"))
        val last = listOf(item("PCP", "1200"), item("TMP", "1300"))
        val result = fetchAllKmaItems { number ->
            when (number) {
                1 -> page(first, 4)
                2 -> page(last, 4)
                else -> error("Unnecessary page request")
            }
        }
        assertEquals(first + last, result)
    }

    @Test fun doesNotRequestAnotherPageWhenForecastIsComplete() = runBlocking {
        val items = listOf(item("PCP", "1200"))
        assertEquals(items, fetchAllKmaItems { number ->
            check(number == 1)
            page(items, 1)
        })
    }

    @Test fun failedLaterPageCannotReturnPartialForecast() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fetchAllKmaItems { number ->
                    if (number == 1) page(listOf(item("TMP", "1200")), 2)
                    else page(emptyList(), 2, "03")
                }
            }
        }
    }

    @Test fun emptyLaterPageCannotSilentlyTruncateForecast() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fetchAllKmaItems { number ->
                    page(if (number == 1) listOf(item("TMP", "1200")) else emptyList(), 2)
                }
            }
        }
    }
}
