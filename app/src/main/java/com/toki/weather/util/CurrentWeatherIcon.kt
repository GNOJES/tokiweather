package com.toki.weather.util

import com.toki.weather.R
import com.toki.weather.data.model.WeatherCondition
import java.time.LocalDateTime

fun currentWeatherIconRes(
    condition: WeatherCondition,
    dateTime: LocalDateTime,
    latitude: Double,
    longitude: Double
): Int {
    if (!SolarTime.isNight(dateTime, latitude, longitude)) return condition.iconRes
    return when (condition) {
        WeatherCondition.CLEAR -> R.drawable.ic_weather_clear_night
        WeatherCondition.CLOUDY -> R.drawable.ic_weather_cloudy_night
        else -> condition.iconRes
    }
}
