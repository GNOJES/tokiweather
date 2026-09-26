package com.toki.weather.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SolarTimeTest {
    private val latitude = 37.5239
    private val longitude = 126.9052

    @Test fun eveningAndDawnUseMoonButMiddayUsesSun() {
        assertFalse(SolarTime.isNight(LocalDateTime.of(2026, 9, 26, 18, 0), latitude, longitude))
        assertTrue(SolarTime.isNight(LocalDateTime.of(2026, 9, 26, 19, 0), latitude, longitude))
        assertTrue(SolarTime.isNight(LocalDateTime.of(2026, 9, 27, 5, 30), latitude, longitude))
        assertFalse(SolarTime.isNight(LocalDateTime.of(2026, 9, 27, 7, 0), latitude, longitude))
    }

    @Test fun summerEveningStaysDaylightLongerThanWinter() {
        assertFalse(SolarTime.isNight(LocalDateTime.of(2026, 7, 1, 19, 0), latitude, longitude))
        assertTrue(SolarTime.isNight(LocalDateTime.of(2026, 1, 1, 19, 0), latitude, longitude))
    }
}
