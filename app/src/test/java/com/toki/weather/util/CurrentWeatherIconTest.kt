package com.toki.weather.util

import com.toki.weather.R
import com.toki.weather.data.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class CurrentWeatherIconTest {
    private val latitude = 37.5239
    private val longitude = 126.9052

    @Test
    fun widgetCurrentClearAndCloudyUseMoonAfterSunset() {
        val night = LocalDateTime.of(2026, 9, 28, 22, 0)
        assertEquals(R.drawable.ic_weather_clear_night, currentWeatherIconRes(WeatherCondition.CLEAR, night, latitude, longitude))
        assertEquals(R.drawable.ic_weather_cloudy_night, currentWeatherIconRes(WeatherCondition.CLOUDY, night, latitude, longitude))
    }

    @Test
    fun daytimeAndRainKeepExistingIcons() {
        val day = LocalDateTime.of(2026, 9, 28, 12, 0)
        val night = LocalDateTime.of(2026, 9, 28, 22, 0)
        assertEquals(R.drawable.ic_weather_clear, currentWeatherIconRes(WeatherCondition.CLEAR, day, latitude, longitude))
        assertEquals(R.drawable.ic_weather_rain, currentWeatherIconRes(WeatherCondition.RAIN, night, latitude, longitude))
    }
}
