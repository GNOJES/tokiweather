package com.toki.weather.data.repository

import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.model.HalfDayForecast
import com.toki.weather.data.model.HourlyForecast
import com.toki.weather.data.remote.KmaResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastSelectionTest {
    private val date = "20260925"

    @Test
    fun currentConditionsUseCurrentHourNotLaterDailyRain() {
        val items = listOf(
            item("SKY", "0000", "1"), item("POP", "0000", "0"), item("PTY", "0000", "0"),
            item("SKY", "1200", "4"), item("POP", "1200", "30"), item("PTY", "1200", "0"),
            item("SKY", "1700", "4"), item("POP", "1700", "60"), item("PTY", "1700", "1")
        )

        assertEquals(ForecastMoment(sky = 1, pop = 0), selectCurrentForecastMoment(items, date, "0000"))
        assertEquals(ForecastMoment(sky = 4, pop = 30), selectCurrentForecastMoment(items, date, "1200"))
        assertEquals(ForecastMoment(sky = 4, pop = 60), selectCurrentForecastMoment(items, date, "1800"))
        assertEquals(DailyForecast(WeatherCondition.RAIN, 60), selectDailyForecast(items, date, "0000"))
    }

    @Test
    fun dailyIconUsesSameRainyPeriodAsDisplayedPeakProbability() {
        val items = listOf(
            item("SKY", "0000", "4"), item("PTY", "0000", "1"), item("POP", "0000", "60"),
            item("SKY", "1200", "1"), item("PTY", "1200", "0"), item("POP", "1200", "0")
        )

        assertEquals(DailyForecast(WeatherCondition.RAIN, 60), selectDailyForecast(items, date, "0000"))
    }

    @Test
    fun samePeakProbabilityPrefersRainOverClearSky() {
        val items = listOf(
            item("SKY", "0000", "1"), item("PTY", "0000", "0"), item("POP", "0000", "60"),
            item("SKY", "1700", "4"), item("PTY", "1700", "1"), item("POP", "1700", "60")
        )

        assertEquals(DailyForecast(WeatherCondition.RAIN, 60), selectDailyForecast(items, date, "0000"))
    }

    @Test
    fun dailyForecastIgnoresElapsedHours() {
        val items = listOf(
            item("SKY", "0000", "4"), item("PTY", "0000", "1"), item("POP", "0000", "60"),
            item("SKY", "1200", "1"), item("PTY", "1200", "0"), item("POP", "1200", "0")
        )

        assertEquals(DailyForecast(WeatherCondition.CLEAR, 0), selectDailyForecast(items, date, "1200"))
    }

    @Test
    fun morningAndAfternoonUseTheirOwnPeakRainAndTemperatureRange() {
        val items = listOf(
            item("SKY", "0600", "1"), item("PTY", "0600", "0"), item("POP", "0600", "0"), item("TMP", "0600", "18"),
            item("SKY", "0900", "4"), item("PTY", "0900", "1"), item("POP", "0900", "60"), item("TMP", "0900", "21"),
            item("SKY", "1200", "3"), item("PTY", "1200", "0"), item("POP", "1200", "20"), item("TMP", "1200", "23"),
            item("SKY", "1500", "1"), item("PTY", "1500", "0"), item("POP", "1500", "0"), item("TMP", "1500", "25")
        )

        assertEquals(HalfDayForecast(WeatherCondition.RAIN, 18, 21, 60), selectHalfDayForecast(items, date, "0600", "1200"))
        assertEquals(HalfDayForecast(WeatherCondition.CLOUDY, 23, 25, 20), selectHalfDayForecast(items, date, "1200", "1800"))
    }

    @Test
    fun missingHalfDayForecastStaysUnavailable() {
        val items = listOf(item("TMP", "1800", "22"))
        assertEquals(null, selectHalfDayForecast(items, date, "0600", "1200"))
    }

    @Test
    fun hourlyForecastGroupsEachFutureHourAcrossMidnight() {
        val nextDate = "20260926"
        val items = listOf(
            item("TMP", "1700", "24"), item("SKY", "1700", "1"),
            item("TMP", "1800", "22"), item("SKY", "1800", "4"),
            item("PTY", "1800", "1"), item("POP", "1800", "60"),
            item("TMP", "0000", "19", nextDate), item("SKY", "0000", "3", nextDate),
            item("PTY", "0000", "0", nextDate), item("POP", "0000", "30", nextDate)
        )

        assertEquals(
            listOf(
                HourlyForecast(date, "1800", WeatherCondition.RAIN, 22, 60),
                HourlyForecast(nextDate, "0000", WeatherCondition.CLOUDY, 19, 30)
            ),
            selectHourlyForecast(items, date, "1800")
        )
    }

    @Test
    fun hourlyForecastIgnoresTemperatureExtremesWithoutHourlyWeather() {
        val items = listOf(item("TMN", "0600", "18"), item("TMP", "0700", "19"), item("SKY", "0700", "1"))
        assertEquals(
            listOf(HourlyForecast(date, "0700", WeatherCondition.CLEAR, 19, null)),
            selectHourlyForecast(items, date, "0600")
        )
    }

    @Test
    fun nearTermUltraForecastOverridesShortTermWeatherAndProbability() {
        val short = listOf(
            HourlyForecast(date, "1200", WeatherCondition.CLEAR, 25, 0),
            HourlyForecast(date, "1300", WeatherCondition.CLEAR, 26, 0),
            HourlyForecast(date, "1900", WeatherCondition.CLEAR, 21, 0)
        )
        val ultra = listOf(
            item("SKY", "1200", "3"), item("PTY", "1200", "0"),
            item("POP", "1200", "20"), item("T1H", "1200", "24"),
            item("SKY", "1300", "4"), item("PTY", "1300", "0"),
            item("POP", "1300", "30"), item("T1H", "1300", "25")
        )

        assertEquals(
            listOf(
                HourlyForecast(date, "1200", WeatherCondition.CLOUDY, 24, 20),
                HourlyForecast(date, "1300", WeatherCondition.OVERCAST, 25, 30),
                HourlyForecast(date, "1900", WeatherCondition.CLEAR, 21, 0)
            ),
            mergeUltraShortForecast(short, ultra)
        )
    }

    @Test
    fun threeHourSummaryKeepsPeakRainAndTemperatureRange() {
        val hourly = listOf(
            HourlyForecast(date, "1200", WeatherCondition.CLEAR, 25, 0),
            HourlyForecast(date, "1300", WeatherCondition.OVERCAST, 26, 30),
            HourlyForecast(date, "1400", WeatherCondition.RAIN, 27, 60),
            HourlyForecast(date, "1500", WeatherCondition.CLEAR, 26, 0)
        )

        assertEquals(
            listOf(
                ThreeHourForecast(date, "1200", "1400", WeatherCondition.RAIN, 25, 27, 60),
                ThreeHourForecast(date, "1500", "1500", WeatherCondition.CLEAR, 26, 26, 0)
            ),
            summarizeThreeHours(hourly)
        )
    }

    @Test
    fun partialThreeHourSummaryUsesClockAlignedLabel() {
        val hourly = listOf(
            HourlyForecast(date, "1900", WeatherCondition.CLEAR, 22, 0),
            HourlyForecast(date, "2000", WeatherCondition.CLEAR, 21, 0),
            HourlyForecast(date, "2100", WeatherCondition.CLOUDY, 20, 20)
        )

        assertEquals(
            listOf(
                ThreeHourForecast(date, "1800", "2000", WeatherCondition.CLEAR, 21, 22, 0),
                ThreeHourForecast(date, "2100", "2100", WeatherCondition.CLOUDY, 20, 20, 20)
            ),
            summarizeThreeHours(hourly)
        )
    }

    private fun item(category: String, time: String, value: String, forecastDate: String = date) = KmaResponse.Item(
        baseDate = "20260924",
        baseTime = "2300",
        category = category,
        fcstDate = forecastDate,
        fcstTime = time,
        fcstValue = value,
        obsrValue = null,
        nx = 59,
        ny = 126
    )
}
