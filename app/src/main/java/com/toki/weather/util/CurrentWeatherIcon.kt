package com.toki.weather.util

import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.model.WidgetIconTheme
import java.time.LocalDateTime

fun currentWeatherIconRes(
    condition: WeatherCondition,
    dateTime: LocalDateTime,
    latitude: Double,
    longitude: Double,
    theme: WidgetIconTheme = WidgetIconTheme.DEFAULT
): Int {
    return theme.iconRes(condition, SolarTime.isNight(dateTime, latitude, longitude))
}
