package com.toki.weather.data.repository

import com.toki.weather.data.remote.KmaNoDataException
import com.toki.weather.data.remote.KmaResponse
import com.toki.weather.util.DateTimeUtils
import java.time.LocalDateTime

data class ShortForecastResult(val items: List<KmaResponse.Item>, val usedPreviousIssue: Boolean)

/** Only NO_DATA may use one previous issue. Never merge partial pages or hide other failures. */
suspend fun fetchShortForecastWithFallback(
    now: LocalDateTime,
    fetch: suspend (date: String, time: String, previous: Boolean) -> List<KmaResponse.Item>
): ShortForecastResult {
    val (date, time) = DateTimeUtils.getVilageFcstBaseDateTime(now)
    return try {
        ShortForecastResult(fetch(date, time, false), false)
    } catch (_: KmaNoDataException) {
        val (previousDate, previousTime) = DateTimeUtils.getPreviousVilageFcstBaseDateTime(now)
        ShortForecastResult(fetch(previousDate, previousTime, true), true)
    }
}
