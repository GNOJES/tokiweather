package com.toki.weather.data.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DatedForecast(
    val date: String,
    val morning: HalfDayForecast?,
    val afternoon: HalfDayForecast?,
    val minimum: Int?,
    val maximum: Int?,
    val condition: WeatherCondition,
    val pop: Int?
)

/** Legacy relative slots are anchored once to the successful fetch date, never to display time. */
private fun CachedWeather.forecastsWithDates(): List<DatedForecast> {
    if (datedForecasts.isNotEmpty() || lastUpdated <= 0) return datedForecasts
    val date = Instant.ofEpochMilli(lastUpdated).atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
    return (0..2).map { offset ->
        DatedForecast(date.plusDays(offset.toLong()).format(DateTimeFormatter.BASIC_ISO_DATE),
            halfDayForecasts.getOrNull(offset * 2), halfDayForecasts.getOrNull(offset * 2 + 1),
            when (offset) { 1 -> tomorrowMin; 2 -> dayAfterMin; else -> null },
            when (offset) { 1 -> tomorrowMax; 2 -> dayAfterMax; else -> null },
            when (offset) { 1 -> tomorrowCondition; 2 -> dayAfterCondition; else -> WeatherCondition.UNKNOWN },
            when (offset) { 1 -> tomorrowPop; 2 -> dayAfterPop; else -> null })
    }
}

/** Pure display projection; do not save it or relabel an old forecast as a new date. */
fun CachedWeather.forDisplay(now: LocalDateTime): CachedWeather {
    val forecasts = forecastsWithDates()
    val dates = (0L..2L).map { now.toLocalDate().plusDays(it).format(DateTimeFormatter.BASIC_ISO_DATE) }
    val days = dates.map { date -> forecasts.firstOrNull { it.date == date } }
    val remaining = hourlyForecasts.filter { it.date + it.time >= dates[0] + "%02d00".format(now.hour) }
    val nowMillis = now.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
    val airCurrent = pmObservedAt != null && nowMillis - pmObservedAt in 0L..3 * 60 * 60 * 1000L
    return copy(
        pm10 = if (airCurrent) pm10 else -1,
        pm25 = if (airCurrent) pm25 else -1,
        datedForecasts = forecasts,
        hourlyForecasts = remaining,
        todayPop = remaining.filter { it.date == dates[0] }.mapNotNull { it.pop }.maxOrNull(),
        halfDayForecasts = days.flatMap { listOf(it?.morning, it?.afternoon) },
        tomorrowMin = days[1]?.minimum, tomorrowMax = days[1]?.maximum,
        tomorrowCondition = days[1]?.condition ?: WeatherCondition.UNKNOWN, tomorrowPop = days[1]?.pop,
        dayAfterMin = days[2]?.minimum, dayAfterMax = days[2]?.maximum,
        dayAfterCondition = days[2]?.condition ?: WeatherCondition.UNKNOWN, dayAfterPop = days[2]?.pop
    )
}
