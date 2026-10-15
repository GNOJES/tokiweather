package com.toki.weather.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherScreenSpacingTest {
    @Test fun disabledKeepsExistingLayoutEvenWithExtraSpace() {
        assertEquals(0f, weatherCardExtraPadding(false, 760f, 560f), 0.01f)
    }

    @Test fun enabledDistributesOnlyUnusedHeightAcrossFiveCards() {
        assertEquals(20f, weatherCardExtraPadding(true, 760f, 560f), 0.01f)
    }

    @Test fun overflowingContentKeepsNaturalSizeForScrolling() {
        assertEquals(0f, weatherCardExtraPadding(true, 560f, 760f), 0.01f)
    }

    @Test fun unmeasuredContentDoesNotStretchBeforeLayout() {
        assertEquals(0f, weatherCardExtraPadding(true, 760f, null), 0.01f)
    }
}
