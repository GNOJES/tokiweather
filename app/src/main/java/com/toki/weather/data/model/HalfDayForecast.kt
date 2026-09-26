package com.toki.weather.data.model

/** 기상청 시간별 단기예보를 오전 또는 오후 구간으로 묶은 값. */
data class HalfDayForecast(
    val condition: WeatherCondition,
    val minTemp: Int?,
    val maxTemp: Int?,
    val pop: Int?
)
