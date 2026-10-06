package com.toki.weather.data.model

import com.toki.weather.data.repository.selectDailyForecast
import org.junit.Assert.*
import org.junit.Test

class ForecastRolloverTest {
    @Test fun missingDailyProbabilityMustNotBecomeZero() {
        assertNull(selectDailyForecast(emptyList(), "20261006", "0000").pop)
    }
}

class DatedCacheDisplayTest {
    private val now = java.time.LocalDateTime.of(2026, 10, 6, 1, 0)
    private val saved = now.minusDays(1).atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
    @org.junit.Test fun midnightSelectsActualDateAndMissingDayStaysMissing() {
        val cache = CachedWeather.EMPTY.copy(lastUpdated = saved, tomorrowMin = 10, dayAfterMin = 20,
            dayAfterPop = 0, dayAfterCondition = WeatherCondition.CLEAR)
        val display = cache.forDisplay(now)
        org.junit.Assert.assertEquals(20, display.tomorrowMin)
        org.junit.Assert.assertEquals(0, display.tomorrowPop)
        org.junit.Assert.assertNull(display.dayAfterMin)
        org.junit.Assert.assertNull(display.dayAfterPop)
        org.junit.Assert.assertEquals(display, display.forDisplay(now))
    }
    @org.junit.Test fun probabilityUsesOnlyRemainingHoursOfCurrentDate() {
        val cache = CachedWeather.EMPTY.copy(lastUpdated = saved, todayPop = 90, hourlyForecasts = listOf(
            HourlyForecast("20261005", "2300", WeatherCondition.CLEAR, 10, 90),
            HourlyForecast("20261006", "0000", WeatherCondition.CLEAR, 10, 80),
            HourlyForecast("20261006", "0100", WeatherCondition.CLEAR, 10, 0),
            HourlyForecast("20261007", "0100", WeatherCondition.CLEAR, 10, 70)))
        org.junit.Assert.assertEquals(0, cache.forDisplay(now).todayPop)
        org.junit.Assert.assertNull(cache.forDisplay(now.plusHours(1)).todayPop)
    }
}

class DateBoundaryTest {
    @Test fun explicitDatesSurviveMonthBoundaryAndUseBothHalfDays() {
        val morning = HalfDayForecast(WeatherCondition.CLEAR, 0, 5, 0)
        val afternoon = HalfDayForecast(WeatherCondition.RAIN, 5, 10, 80)
        val cache = CachedWeather.EMPTY.copy(lastUpdated = 1,
            datedForecasts = listOf(DatedForecast("20270101", morning, afternoon, 0, 10, WeatherCondition.RAIN, 80)))
        val before = cache.forDisplay(java.time.LocalDateTime.of(2026, 12, 31, 23, 59))
        assertEquals(0, before.tomorrowMin)
        assertEquals(80, before.tomorrowPop)
        assertEquals(morning, before.halfDayForecasts[2])
        assertEquals(afternoon, before.halfDayForecasts[3])
        val after = cache.forDisplay(java.time.LocalDateTime.of(2027, 1, 1, 0, 0))
        assertNull(after.tomorrowMin)
        assertNull(after.tomorrowPop)
        assertEquals(morning, after.halfDayForecasts[0])
    }
    @Test fun expiredAirCannotLookCurrentWhenCacheIsRedrawnWithoutFetch() {
        val now = java.time.LocalDateTime.of(2026, 10, 6, 10, 0)
        val time = now.atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        val cache = CachedWeather.EMPTY.copy(pm10 = 20, pm25 = 10, pmObservedAt = time - 4 * 60 * 60_000)
        assertEquals(-1, cache.forDisplay(now).pm10)
        assertEquals(-1, cache.forDisplay(now).pm25)
        assertEquals(cache.pmObservedAt, cache.forDisplay(now).pmObservedAt)
    }
}
