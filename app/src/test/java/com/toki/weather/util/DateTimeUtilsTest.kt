package com.toki.weather.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class DateTimeUtilsTest {
    @Test fun ultraShortForecastTriesCurrentHourEvenBeforeTwentyMinutes() {
        assertEquals(
            "20260926" to "0000",
            DateTimeUtils.getUltraSrtFcstBaseDateTime(LocalDateTime.of(2026, 9, 26, 0, 19))
        )
        assertEquals(
            "20260926" to "1900",
            DateTimeUtils.getUltraSrtFcstBaseDateTime(LocalDateTime.of(2026, 9, 26, 19, 18))
        )
    }

    @Test fun previousShortTermIssueCrossesMidnightCorrectly() {
        assertEquals(
            "20260926" to "2000",
            DateTimeUtils.getPreviousVilageFcstBaseDateTime(LocalDateTime.of(2026, 9, 26, 23, 30))
        )
        assertEquals(
            "20260926" to "2300",
            DateTimeUtils.getPreviousVilageFcstBaseDateTime(LocalDateTime.of(2026, 9, 27, 2, 20))
        )
    }
}
