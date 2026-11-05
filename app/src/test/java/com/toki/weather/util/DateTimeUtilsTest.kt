package com.toki.weather.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
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
    @Test fun forecastDatesIncludeKoreanWeekdaysAndRequestedSpacing() {
        val today = LocalDate.of(2026, 11, 5)
        assertEquals("11/6 (금)", DateTimeUtils.forecastDateLabel(today, 1))
        assertEquals("11/7 (토)", DateTimeUtils.forecastDateLabel(today, 2))
    }

    @Test fun forecastDatesCrossYearAndLeapDay() {
        assertEquals("1/1 (금)", DateTimeUtils.forecastDateLabel(LocalDate.of(2026, 12, 31), 1))
        assertEquals("2/29 (화)", DateTimeUtils.forecastDateLabel(LocalDate.of(2028, 2, 28), 1))
        assertEquals("3/1 (수)", DateTimeUtils.forecastDateLabel(LocalDate.of(2028, 2, 28), 2))
    }

    @Test fun forecastTitleAdvancesWithDisplayDateEvenWithoutNewCache() {
        assertEquals("11/7 (토)", DateTimeUtils.forecastDateLabel(LocalDate.of(2026, 11, 6), 1))
        assertEquals("11/8 (일)", DateTimeUtils.forecastDateLabel(LocalDate.of(2026, 11, 6), 2))
    }
}
