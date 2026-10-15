package com.toki.weather.data.model

import com.toki.weather.R
import org.junit.Assert.*
import org.junit.Test

class WidgetIconThemeTest {
    @Test fun missingOrUnknownSavedThemeKeepsExistingIcons() {
        assertEquals(WidgetIconTheme.DEFAULT, WidgetIconTheme.fromId(null))
        assertEquals(WidgetIconTheme.DEFAULT, WidgetIconTheme.fromId("future-pack"))
        WeatherCondition.entries.forEach { condition ->
            assertEquals(condition.iconRes, WidgetIconTheme.DEFAULT.iconRes(condition))
        }
    }

    @Test fun eachNewPackCoversEveryConditionAndBothNightIcons() {
        val allNewResources = mutableSetOf<Int>()
        WidgetIconTheme.entries.filter { it != WidgetIconTheme.DEFAULT }.forEach { theme ->
            val day = WeatherCondition.entries.map { theme.iconRes(it) }
            assertEquals(8, day.toSet().size)
            val night = listOf(theme.iconRes(WeatherCondition.CLEAR, true), theme.iconRes(WeatherCondition.CLOUDY, true))
            assertTrue(night.none { it in day })
            (day + night).forEach { assertTrue("Resource must be unique to its theme", allNewResources.add(it)) }
        }
        assertEquals(60, allNewResources.size)
    }

    @Test fun onlyClearAndCloudyChangeAtNight() {
        WidgetIconTheme.entries.forEach { theme ->
            WeatherCondition.entries.filter { it !in listOf(WeatherCondition.CLEAR, WeatherCondition.CLOUDY) }.forEach {
                assertEquals(theme.iconRes(it), theme.iconRes(it, true))
            }
        }
        assertEquals(R.drawable.ic_weather_clear_night, WidgetIconTheme.DEFAULT.iconRes(WeatherCondition.CLEAR, true))
        assertEquals(R.drawable.ic_weather_cloudy_night, WidgetIconTheme.DEFAULT.iconRes(WeatherCondition.CLOUDY, true))
    }

    @Test fun persistentIdsRoundTripForExactlySevenOptions() {
        assertEquals(7, WidgetIconTheme.entries.size)
        assertEquals(listOf("default", "mochi", "plush", "jelly", "sticker", "pixel", "crayon"),
            WidgetIconTheme.entries.map { it.id })
        WidgetIconTheme.entries.forEach { assertEquals(it, WidgetIconTheme.fromId(it.id)) }
    }
}
