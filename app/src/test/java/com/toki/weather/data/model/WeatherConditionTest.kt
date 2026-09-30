package com.toki.weather.data.model

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherConditionTest {

    @Test
    fun ultraShortPrecipitationCodesFiveThroughSevenHaveWeatherIcons() {
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromCodes(5, 4))
        assertEquals(WeatherCondition.SLEET, WeatherCondition.fromCodes(6, 4))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromCodes(7, 4))
    }

    @Test
    fun allConditionsHaveValidProperties() {
        for (condition in WeatherCondition.entries) {
            assertTrue("Label should not be empty for $condition", condition.label.isNotEmpty())
            assertTrue("Emoji should not be empty for $condition", condition.emoji.isNotEmpty())
            assertNotEquals("IconRes should be non-zero for $condition", 0, condition.iconRes)
        }
    }
}
