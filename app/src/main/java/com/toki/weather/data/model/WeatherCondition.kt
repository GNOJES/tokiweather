package com.toki.weather.data.model

import androidx.annotation.DrawableRes
import com.toki.weather.R

/**
 * 날씨 상태 enum
 * SKY(하늘상태)와 PTY(강수형태) 코드를 기반으로 결정
 * PTY > 0 이면 PTY 우선, PTY = 0 이면 SKY 사용
 */
enum class WeatherCondition(
    val label: String,
    val emoji: String,
    @DrawableRes val iconRes: Int
) {
    CLEAR("맑음", "☀️", R.drawable.ic_weather_clear),
    CLOUDY("구름많음", "⛅", R.drawable.ic_weather_cloudy),
    OVERCAST("흐림", "☁️", R.drawable.ic_weather_overcast),
    RAIN("비", "🌧️", R.drawable.ic_weather_rain),
    SLEET("비/눈", "🌨️", R.drawable.ic_weather_sleet),
    SNOW("눈", "❄️", R.drawable.ic_weather_snow),
    SHOWER("소나기", "🌦️", R.drawable.ic_weather_shower),
    UNKNOWN("알수없음", "❓", R.drawable.ic_weather_unknown);

    companion object {
        /**
         * PTY(강수형태)와 SKY(하늘상태) 코드로 날씨 상태 결정
         * @param pty 강수형태 코드 (0:없음, 1:비, 2:비/눈, 3:눈, 4:소나기)
         * @param sky 하늘상태 코드 (1:맑음, 3:구름많음, 4:흐림)
         */
        fun fromCodes(pty: Int, sky: Int): WeatherCondition {
            // 강수형태가 있으면 우선
            if (pty > 0) {
                return when (pty) {
                    1 -> RAIN
                    2 -> SLEET
                    3 -> SNOW
                    4 -> SHOWER
                    else -> UNKNOWN
                }
            }
            // 강수 없으면 하늘상태로 판단
            return when (sky) {
                1 -> CLEAR
                3 -> CLOUDY
                4 -> OVERCAST
                else -> UNKNOWN
            }
        }
    }
}
