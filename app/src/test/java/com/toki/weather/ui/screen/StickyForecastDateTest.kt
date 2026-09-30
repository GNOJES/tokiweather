package com.toki.weather.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class StickyForecastDateTest {
    @Test
    fun dateStaysUntilNextDateTextReachesPinnedDate() {
        val dates = listOf("20260930", "20260930", "20261001", "20261001")
        fun at(scroll: Int) = stickyForecastDate(dates, scroll, 48, 5, 5, 34)

        assertEquals(StickyForecastDate("20260930"), at(0))
        assertEquals(StickyForecastDate("20260930"), at(81))
        assertEquals(StickyForecastDate("20261001"), at(82))
    }
}
