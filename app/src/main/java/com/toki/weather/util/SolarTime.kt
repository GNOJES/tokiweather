package com.toki.weather.util

import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 위치와 날짜에 따른 일출·일몰 전후를 판별한다. 입력 시각은 한국 표준시다. */
object SolarTime {
    fun isNight(dateTime: LocalDateTime, latitude: Double, longitude: Double): Boolean {
        // NOAA Solar Calculation 식의 균시차·적위·태양 고도. 표준 일출/일몰의 천정각은 90.833도.
        // https://gml.noaa.gov/grad/solcalc/solareqns.PDF
        val minutes = dateTime.hour * 60.0 + dateTime.minute + dateTime.second / 60.0
        val yearLength = if (dateTime.toLocalDate().isLeapYear) 366.0 else 365.0
        val angle = 2.0 * PI / yearLength * (dateTime.dayOfYear - 1 + (minutes / 60.0 - 12.0) / 24.0)
        val equationOfTime = 229.18 * (
            0.000075 + 0.001868 * cos(angle) - 0.032077 * sin(angle) -
                0.014615 * cos(2 * angle) - 0.040849 * sin(2 * angle)
            )
        val declination = 0.006918 - 0.399912 * cos(angle) + 0.070257 * sin(angle) -
            0.006758 * cos(2 * angle) + 0.000907 * sin(2 * angle) -
            0.002697 * cos(3 * angle) + 0.00148 * sin(3 * angle)
        val solarMinutes = (minutes + equationOfTime + 4.0 * longitude - 540.0).let {
            ((it % 1440.0) + 1440.0) % 1440.0
        }
        val hourAngle = Math.toRadians(solarMinutes / 4.0 - 180.0)
        val latRadians = Math.toRadians(latitude)
        val cosZenith = sin(latRadians) * sin(declination) +
            cos(latRadians) * cos(declination) * cos(hourAngle)
        return cosZenith < cos(Math.toRadians(90.833))
    }
}
