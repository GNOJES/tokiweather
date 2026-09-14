package com.toki.weather.data.model

import androidx.compose.ui.graphics.Color

/**
 * 위젯에 표시할 날씨 및 대기질 데이터 모델
 */
data class CachedWeather(
    val locationName: String = "설정 위치",
    val currentTemp: Int,
    val currentCondition: WeatherCondition,
    val todayPop: Int = 0,         // 오늘 남은 시간대 최대 강수확률 (%)
    val pm10: Int = -1,            // 미세먼지 수치 (㎍/㎥, -1은 미수신)
    val pm25: Int = -1,            // 초미세먼지 수치 (㎍/㎥, -1은 미수신)
    val tomorrowMin: Int,
    val tomorrowMax: Int,
    val tomorrowCondition: WeatherCondition,
    val tomorrowPop: Int = 0,      // 내일 최대 강수확률 (%)
    val dayAfterMin: Int,
    val dayAfterMax: Int,
    val dayAfterCondition: WeatherCondition,
    val dayAfterPop: Int = 0,      // 모레 최대 강수확률 (%)
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * 환경부 기준 미세먼지(PM10) 4단계 등급 색상
     * 좋음: 0~30(파랑), 보통: 31~80(초록), 나쁨: 81~150(주황), 매우나쁨: 151~(빨강)
     */
    fun getPm10Color(): Color {
        return when {
            pm10 < 0 -> Color(0xFFB0B0B0)
            pm10 <= 30 -> Color(0xFF4AA3FF)
            pm10 <= 80 -> Color(0xFF22C55E)
            pm10 <= 150 -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        }
    }

    /**
     * 환경부 기준 초미세먼지(PM2.5) 4단계 등급 색상
     * 좋음: 0~15(파랑), 보통: 16~35(초록), 나쁨: 36~75(주황), 매우나쁨: 76~(빨강)
     */
    fun getPm25Color(): Color {
        return when {
            pm25 < 0 -> Color(0xFFB0B0B0)
            pm25 <= 15 -> Color(0xFF4AA3FF)
            pm25 <= 35 -> Color(0xFF22C55E)
            pm25 <= 75 -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        }
    }

    companion object {
        val EMPTY = CachedWeather(
            locationName = "위치 확인 중",
            currentTemp = 0,
            currentCondition = WeatherCondition.UNKNOWN,
            todayPop = 0,
            pm10 = -1,
            pm25 = -1,
            tomorrowMin = 0,
            tomorrowMax = 0,
            tomorrowCondition = WeatherCondition.UNKNOWN,
            tomorrowPop = 0,
            dayAfterMin = 0,
            dayAfterMax = 0,
            dayAfterCondition = WeatherCondition.UNKNOWN,
            dayAfterPop = 0,
            lastUpdated = 0L
        )
    }
}
