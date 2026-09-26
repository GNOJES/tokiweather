package com.toki.weather.data.repository

import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.model.HalfDayForecast
import com.toki.weather.data.model.HourlyForecast
import com.toki.weather.data.remote.KmaResponse

data class ForecastMoment(val sky: Int?, val pop: Int?)

data class DailyForecast(val condition: WeatherCondition, val pop: Int)

/** 현재 시각에 해당하는 단기예보의 하늘상태와 강수확률을 찾는다. */
fun selectCurrentForecastMoment(
    items: List<KmaResponse.Item>,
    date: String,
    currentHour: String
): ForecastMoment {
    fun valueAtCurrentHour(category: String): Int? {
        val candidates = items.filter {
            it.category == category && it.fcstDate == date && it.fcstTime != null
        }
        val current = candidates.filter { it.fcstTime!! <= currentHour }
            .maxByOrNull { it.fcstTime!! }
        return (current ?: candidates.minByOrNull { it.fcstTime!! })?.fcstValue?.toIntOrNull()
    }
    return ForecastMoment(
        sky = valueAtCurrentHour("SKY"),
        pop = valueAtCurrentHour("POP")
    )
}

/** 하루 한 아이콘을 유지할 때 최고 강수확률 시간대의 날씨를 함께 선택한다. */
fun selectDailyForecast(
    items: List<KmaResponse.Item>,
    date: String,
    fromTime: String
): DailyForecast {
    val slots = items.filter { it.fcstDate == date && it.fcstTime != null && it.fcstTime >= fromTime }
        .groupBy { it.fcstTime!! }
        .map { (time, values) ->
            val byCategory = values.associate { it.category to it.fcstValue?.toIntOrNull() }
            ForecastSlot(time, byCategory["SKY"], byCategory["PTY"], byCategory["POP"])
        }
    val selected = slots.sortedWith(
        compareByDescending<ForecastSlot> { it.pop ?: -1 }
            .thenByDescending { (it.pty ?: 0) > 0 }
            .thenByDescending { it.sky ?: -1 }
            .thenBy { it.time }
    ).firstOrNull() ?: return DailyForecast(WeatherCondition.UNKNOWN, 0)
    return DailyForecast(
        condition = WeatherCondition.fromCodes(selected.pty ?: 0, selected.sky ?: -1),
        pop = slots.mapNotNull { it.pop }.maxOrNull() ?: 0
    )
}

private data class ForecastSlot(val time: String, val sky: Int?, val pty: Int?, val pop: Int?)

data class ThreeHourForecast(
    val date: String,
    val startTime: String,
    val endTime: String,
    val condition: WeatherCondition,
    val minTemp: Int?,
    val maxTemp: Int?,
    val pop: Int?
)

/** 현재 시각 이후의 SKY/PTY/TMP/POP를 시각별로 묶어 시간 순서로 반환한다. */
fun selectHourlyForecast(
    items: List<KmaResponse.Item>,
    currentDate: String,
    currentHour: String
): List<HourlyForecast> = items
    .filter {
        it.category in setOf("SKY", "PTY", "TMP", "POP") &&
            it.fcstDate != null && it.fcstTime != null &&
            it.fcstDate + it.fcstTime >= currentDate + currentHour
    }
    .groupBy { it.fcstDate!! to it.fcstTime!! }
    .toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
    .map { (dateTime, values) ->
        val byCategory = values.associate { it.category to it.fcstValue?.toIntOrNull() }
        HourlyForecast(
            date = dateTime.first,
            time = dateTime.second,
            condition = WeatherCondition.fromCodes(byCategory["PTY"] ?: 0, byCategory["SKY"] ?: -1),
            temperature = byCategory["TMP"],
            pop = byCategory["POP"]
        )
    }

/** 가까운 시간대는 초단기예보의 날씨·기온·강수확률을 우선한다. */
fun mergeUltraShortForecast(
    shortTerm: List<HourlyForecast>,
    ultraItems: List<KmaResponse.Item>
): List<HourlyForecast> {
    val ultraByTime = ultraItems.filter { it.fcstDate != null && it.fcstTime != null }
        .groupBy { it.fcstDate!! to it.fcstTime!! }
    return shortTerm.map { short ->
        val values = ultraByTime[short.date to short.time].orEmpty()
            .associate { it.category to it.fcstValue }
        val sky = values["SKY"]?.toIntOrNull()
        val pty = values["PTY"]?.toIntOrNull()
        short.copy(
            condition = if (sky != null || pty != null) {
                WeatherCondition.fromCodes(pty ?: 0, sky ?: -1)
            } else short.condition,
            temperature = values["T1H"]?.toDoubleOrNull()?.toInt() ?: short.temperature,
            pop = values["POP"]?.toIntOrNull() ?: short.pop
        )
    }
}

/** 00·03·06시 기준 묶음의 강수확률 최고값과 기온 범위를 표시한다. */
fun summarizeThreeHours(hourly: List<HourlyForecast>): List<ThreeHourForecast> = hourly
    .groupBy { it.date to ((it.time.take(2).toIntOrNull() ?: 0) / 3) }
    .toSortedMap(compareBy<Pair<String, Int>> { it.first }.thenBy { it.second })
    .map { (key, values) ->
        val sorted = values.sortedBy { it.time }
        val peak = sorted.maxWithOrNull(
            compareBy<HourlyForecast> { it.pop ?: -1 }
                .thenBy { it.condition == WeatherCondition.RAIN }
        ) ?: sorted.first()
        val temperatures = sorted.mapNotNull { it.temperature }
        ThreeHourForecast(
            date = key.first,
            startTime = String.format("%02d00", key.second * 3),
            endTime = sorted.last().time,
            condition = peak.condition,
            minTemp = temperatures.minOrNull(),
            maxTemp = temperatures.maxOrNull(),
            pop = sorted.mapNotNull { it.pop }.maxOrNull()
        )
    }

/** startTime 이상, endTime 미만의 시간별 자료로 오전·오후 예보를 만든다. */
fun selectHalfDayForecast(
    items: List<KmaResponse.Item>,
    date: String,
    startTime: String,
    endTime: String
): HalfDayForecast? {
    val periodItems = items.filter {
        it.fcstDate == date && it.fcstTime != null && it.fcstTime >= startTime && it.fcstTime < endTime
    }
    if (periodItems.isEmpty()) return null
    val temperatures = periodItems.filter { it.category == "TMP" }
        .mapNotNull { it.fcstValue?.toDoubleOrNull()?.toInt() }
    val summary = selectDailyForecast(periodItems, date, startTime)
    return HalfDayForecast(
        condition = summary.condition,
        minTemp = temperatures.minOrNull(),
        maxTemp = temperatures.maxOrNull(),
        pop = periodItems.filter { it.category == "POP" }
            .mapNotNull { it.fcstValue?.toIntOrNull() }.maxOrNull()
    )
}
