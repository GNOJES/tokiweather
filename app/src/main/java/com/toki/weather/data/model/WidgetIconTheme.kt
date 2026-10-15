package com.toki.weather.data.model

import androidx.annotation.DrawableRes
import com.toki.weather.R

/** Stable persisted IDs; app/tab icons remain unchanged unless a caller explicitly opts in. */
enum class WidgetIconTheme(
    val id: String, val label: String, private val day: List<Int>,
    @DrawableRes private val clearNight: Int, @DrawableRes private val cloudyNight: Int
) {
    DEFAULT("default", "기본", WeatherCondition.entries.map { it.iconRes },
        R.drawable.ic_weather_clear_night, R.drawable.ic_weather_cloudy_night),
    MOCHI("mochi", "모찌 토끼", listOf(
        R.drawable.ic_pack_mochi_clear, R.drawable.ic_pack_mochi_cloudy, R.drawable.ic_pack_mochi_overcast,
        R.drawable.ic_pack_mochi_rain, R.drawable.ic_pack_mochi_sleet, R.drawable.ic_pack_mochi_snow,
        R.drawable.ic_pack_mochi_shower, R.drawable.ic_pack_mochi_unknown),
        R.drawable.ic_pack_mochi_clear_night, R.drawable.ic_pack_mochi_cloudy_night),
    PLUSH("plush", "보송 인형", listOf(
        R.drawable.ic_pack_plush_clear, R.drawable.ic_pack_plush_cloudy, R.drawable.ic_pack_plush_overcast,
        R.drawable.ic_pack_plush_rain, R.drawable.ic_pack_plush_sleet, R.drawable.ic_pack_plush_snow,
        R.drawable.ic_pack_plush_shower, R.drawable.ic_pack_plush_unknown),
        R.drawable.ic_pack_plush_clear_night, R.drawable.ic_pack_plush_cloudy_night),
    JELLY("jelly", "젤리 캔디", listOf(
        R.drawable.ic_pack_jelly_clear, R.drawable.ic_pack_jelly_cloudy, R.drawable.ic_pack_jelly_overcast,
        R.drawable.ic_pack_jelly_rain, R.drawable.ic_pack_jelly_sleet, R.drawable.ic_pack_jelly_snow,
        R.drawable.ic_pack_jelly_shower, R.drawable.ic_pack_jelly_unknown),
        R.drawable.ic_pack_jelly_clear_night, R.drawable.ic_pack_jelly_cloudy_night),
    STICKER("sticker", "납작 스티커", listOf(
        R.drawable.ic_pack_sticker_clear, R.drawable.ic_pack_sticker_cloudy, R.drawable.ic_pack_sticker_overcast,
        R.drawable.ic_pack_sticker_rain, R.drawable.ic_pack_sticker_sleet, R.drawable.ic_pack_sticker_snow,
        R.drawable.ic_pack_sticker_shower, R.drawable.ic_pack_sticker_unknown),
        R.drawable.ic_pack_sticker_clear_night, R.drawable.ic_pack_sticker_cloudy_night),
    PIXEL("pixel", "레트로 픽셀", listOf(
        R.drawable.ic_pack_pixel_clear, R.drawable.ic_pack_pixel_cloudy, R.drawable.ic_pack_pixel_overcast,
        R.drawable.ic_pack_pixel_rain, R.drawable.ic_pack_pixel_sleet, R.drawable.ic_pack_pixel_snow,
        R.drawable.ic_pack_pixel_shower, R.drawable.ic_pack_pixel_unknown),
        R.drawable.ic_pack_pixel_clear_night, R.drawable.ic_pack_pixel_cloudy_night),
    CRAYON("crayon", "크레용 그림책", listOf(
        R.drawable.ic_pack_crayon_clear, R.drawable.ic_pack_crayon_cloudy, R.drawable.ic_pack_crayon_overcast,
        R.drawable.ic_pack_crayon_rain, R.drawable.ic_pack_crayon_sleet, R.drawable.ic_pack_crayon_snow,
        R.drawable.ic_pack_crayon_shower, R.drawable.ic_pack_crayon_unknown),
        R.drawable.ic_pack_crayon_clear_night, R.drawable.ic_pack_crayon_cloudy_night);

    @DrawableRes fun iconRes(condition: WeatherCondition, night: Boolean = false): Int = when {
        night && condition == WeatherCondition.CLEAR -> clearNight
        night && condition == WeatherCondition.CLOUDY -> cloudyNight
        else -> day[condition.ordinal]
    }

    companion object {
        fun fromId(id: String?): WidgetIconTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
