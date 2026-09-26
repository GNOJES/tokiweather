package com.toki.weather.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class KmaResponseValidationTest {
    private fun item(category: String, value: String?, date: String? = null) = KmaResponse.Item(
        baseDate = "20260924", baseTime = "0500", category = category,
        fcstDate = date, fcstTime = "1200", fcstValue = value, obsrValue = value,
        nx = 60, ny = 127
    )

    private fun response(code: String, items: List<KmaResponse.Item>) = KmaResponse(
        KmaResponse.Response(
            header = KmaResponse.Header(code, "message"),
            body = KmaResponse.Body(KmaResponse.Items(items), items.size)
        )
    )

    @Test fun failedApiResultCannotBecomeWeather() {
        assertThrows(IllegalStateException::class.java) {
            requireKmaItems(response("03", listOf(item("T1H", "21"))))
        }
    }

    @Test fun missingOrInvalidCurrentTemperatureCannotBecomeZero() {
        assertThrows(IllegalStateException::class.java) {
            requireCurrentTemperature(listOf(item("PTY", "0")))
        }
        assertThrows(IllegalStateException::class.java) {
            requireCurrentTemperature(listOf(item("T1H", "bad")))
        }
        assertEquals(0, requireCurrentTemperature(listOf(item("T1H", "0"))))
    }

    @Test fun missingForecastTemperatureIsAbsentRatherThanZero() {
        val items = listOf(item("TMN", "0", "20260925"))
        assertEquals(0, forecastTemperature(items, "20260925", "TMN"))
        assertNull(forecastTemperature(items, "20260925", "TMX"))
    }

    @Test fun humidityUsesObservedRelativeHumidityAndKeepsMissingValueAbsent() {
        assertEquals(61, currentHumidity(listOf(item("REH", "61"))))
        assertNull(currentHumidity(listOf(item("PTY", "0"))))
        assertNull(currentHumidity(listOf(item("REH", "bad"))))
    }
}
