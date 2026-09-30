package com.toki.weather.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherMeasurementsTest {
    @Test
    fun apparentTemperatureNeedsAllThreeValidObservations() {
        assertEquals(null, apparentTemperatureCelsius(20.0, 60, null))
        assertEquals(null, apparentTemperatureCelsius(20.0, null, 2.0))
        assertEquals(null, apparentTemperatureCelsius(20.0, 101, 2.0))
        assertEquals(null, apparentTemperatureCelsius(20.0, 60, -1.0))
        assertEquals(19, apparentTemperatureCelsius(20.0, 60, 2.0))
    }

    @Test
    fun hourlyRainfallSeparatesDryMissingAndMeasuredValues() {
        assertEquals("-", formatHourlyRainfall("강수없음"))
        assertEquals("-", formatHourlyRainfall("0.0mm"))
        assertEquals("—", formatHourlyRainfall(null))
        assertEquals("<1mm", formatHourlyRainfall("1mm 미만"))
        assertEquals("2.5mm", formatHourlyRainfall("2.5mm"))
    }

    @Test
    fun threeHourRainfallAddsHoursWithoutInventingMissingValues() {
        assertEquals("-", summarizeRainfall(listOf("강수없음", "0", "0.0mm")))
        assertEquals("3.5mm", summarizeRainfall(listOf("1.0mm", "2.5mm", "강수없음")))
        assertEquals("2~3mm", summarizeRainfall(listOf("1mm 미만", "2.0mm")))
        assertEquals("—", summarizeRainfall(listOf("1.0mm", null)))
    }
}
