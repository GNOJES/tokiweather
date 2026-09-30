package com.toki.weather.data.model

/** 기상청 단기예보의 한 시간대. */
data class HourlyForecast(
    val date: String,
    val time: String,
    val condition: WeatherCondition,
    val temperature: Int?,
    val pop: Int?,
    val precipitation: String? = null // 기상청 PCP 또는 가까운 시간의 RN1
)
