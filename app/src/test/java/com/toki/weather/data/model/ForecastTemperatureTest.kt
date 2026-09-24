package com.toki.weather.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastTemperatureTest {
    @Test fun zeroIsRealButMissingValueIsNot() {
        assertEquals("0° / —", formatTemperatureRange(0, null))
        assertEquals("—~0°", formatTemperatureRange(null, 0, "~"))
    }
}
