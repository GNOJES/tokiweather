package com.toki.weather.data.model

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherConditionTest {

    @Test
    fun allConditionsHaveValidProperties() {
        for (condition in WeatherCondition.entries) {
            assertTrue("Label should not be empty for $condition", condition.label.isNotEmpty())
            assertTrue("Emoji should not be empty for $condition", condition.emoji.isNotEmpty())
            assertNotEquals("IconRes should be non-zero for $condition", 0, condition.iconRes)
        }
    }
}
