package com.toki.weather.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.model.WidgetThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore를 사용한 날씨 데이터 및 위젯 스타일 설정 캐싱
 */
private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "weather_cache"
)

class WeatherDataStore(private val context: Context) {

    companion object {
        // 날씨 데이터 키
        private val KEY_LOCATION_NAME = stringPreferencesKey("location_name")
        private val KEY_CURRENT_TEMP = intPreferencesKey("current_temp")
        private val KEY_CURRENT_CONDITION = stringPreferencesKey("current_condition")
        private val KEY_TODAY_POP = intPreferencesKey("today_pop")
        private val KEY_PM10 = intPreferencesKey("pm10")
        private val KEY_PM25 = intPreferencesKey("pm25")
        private val KEY_TOMORROW_MIN = intPreferencesKey("tomorrow_min")
        private val KEY_TOMORROW_MAX = intPreferencesKey("tomorrow_max")
        private val KEY_TOMORROW_CONDITION = stringPreferencesKey("tomorrow_condition")
        private val KEY_TOMORROW_POP = intPreferencesKey("tomorrow_pop")
        private val KEY_DAY_AFTER_MIN = intPreferencesKey("day_after_min")
        private val KEY_DAY_AFTER_MAX = intPreferencesKey("day_after_max")
        private val KEY_DAY_AFTER_CONDITION = stringPreferencesKey("day_after_condition")
        private val KEY_DAY_AFTER_POP = intPreferencesKey("day_after_pop")
        private val KEY_LAST_UPDATED = longPreferencesKey("last_updated")

        // 위젯 커스텀 테마 키
        private val KEY_BG_COLOR = stringPreferencesKey("theme_bg_color")
        private val KEY_BG_ALPHA = floatPreferencesKey("theme_bg_alpha")
        private val KEY_TEXT_COLOR = stringPreferencesKey("theme_text_color")
        private val KEY_SHADOW_ENABLED = booleanPreferencesKey("theme_shadow_enabled")

        // 갱신 주기 키 (분 단위, 기본 30분)
        private val KEY_UPDATE_INTERVAL = intPreferencesKey("update_interval_minutes")
    }

    /**
     * 캐시된 날씨 데이터를 Flow로 관찰
     */
    val weatherFlow: Flow<CachedWeather> = context.weatherDataStore.data.map { prefs ->
        CachedWeather(
            locationName = prefs[KEY_LOCATION_NAME] ?: "설정 위치",
            currentTemp = prefs[KEY_CURRENT_TEMP] ?: 0,
            currentCondition = prefs[KEY_CURRENT_CONDITION]?.let {
                try { WeatherCondition.valueOf(it) } catch (_: Exception) { WeatherCondition.UNKNOWN }
            } ?: WeatherCondition.UNKNOWN,
            todayPop = prefs[KEY_TODAY_POP] ?: 0,
            pm10 = prefs[KEY_PM10] ?: -1,
            pm25 = prefs[KEY_PM25] ?: -1,
            tomorrowMin = prefs[KEY_TOMORROW_MIN] ?: 0,
            tomorrowMax = prefs[KEY_TOMORROW_MAX] ?: 0,
            tomorrowCondition = prefs[KEY_TOMORROW_CONDITION]?.let {
                try { WeatherCondition.valueOf(it) } catch (_: Exception) { WeatherCondition.UNKNOWN }
            } ?: WeatherCondition.UNKNOWN,
            tomorrowPop = prefs[KEY_TOMORROW_POP] ?: 0,
            dayAfterMin = prefs[KEY_DAY_AFTER_MIN] ?: 0,
            dayAfterMax = prefs[KEY_DAY_AFTER_MAX] ?: 0,
            dayAfterCondition = prefs[KEY_DAY_AFTER_CONDITION]?.let {
                try { WeatherCondition.valueOf(it) } catch (_: Exception) { WeatherCondition.UNKNOWN }
            } ?: WeatherCondition.UNKNOWN,
            dayAfterPop = prefs[KEY_DAY_AFTER_POP] ?: 0,
            lastUpdated = prefs[KEY_LAST_UPDATED] ?: 0L
        )
    }

    /**
     * 위젯 커스텀 테마 설정을 Flow로 관찰
     */
    val themeFlow: Flow<WidgetThemeConfig> = context.weatherDataStore.data.map { prefs ->
        WidgetThemeConfig(
            backgroundColorHex = prefs[KEY_BG_COLOR] ?: "#261643",
            backgroundAlpha = prefs[KEY_BG_ALPHA] ?: 0.8f,
            textColorHex = prefs[KEY_TEXT_COLOR] ?: "#FFFFFF",
            isShadowEnabled = prefs[KEY_SHADOW_ENABLED] ?: true
        )
    }

    /**
     * 갱신 주기 Flow (분 단위, 기본값 30분)
     */
    val updateIntervalFlow: Flow<Int> = context.weatherDataStore.data.map { prefs ->
        prefs[KEY_UPDATE_INTERVAL] ?: 30
    }

    /**
     * 날씨 데이터 저장
     */
    suspend fun save(weather: CachedWeather) {
        context.weatherDataStore.edit { prefs ->
            prefs[KEY_LOCATION_NAME] = weather.locationName
            prefs[KEY_CURRENT_TEMP] = weather.currentTemp
            prefs[KEY_CURRENT_CONDITION] = weather.currentCondition.name
            prefs[KEY_TODAY_POP] = weather.todayPop
            prefs[KEY_PM10] = weather.pm10
            prefs[KEY_PM25] = weather.pm25
            prefs[KEY_TOMORROW_MIN] = weather.tomorrowMin
            prefs[KEY_TOMORROW_MAX] = weather.tomorrowMax
            prefs[KEY_TOMORROW_CONDITION] = weather.tomorrowCondition.name
            prefs[KEY_TOMORROW_POP] = weather.tomorrowPop
            prefs[KEY_DAY_AFTER_MIN] = weather.dayAfterMin
            prefs[KEY_DAY_AFTER_MAX] = weather.dayAfterMax
            prefs[KEY_DAY_AFTER_CONDITION] = weather.dayAfterCondition.name
            prefs[KEY_DAY_AFTER_POP] = weather.dayAfterPop
            prefs[KEY_LAST_UPDATED] = weather.lastUpdated
        }
    }

    /**
     * 위젯 커스텀 테마 저장
     */
    suspend fun saveTheme(theme: WidgetThemeConfig) {
        context.weatherDataStore.edit { prefs ->
            prefs[KEY_BG_COLOR] = theme.backgroundColorHex
            prefs[KEY_BG_ALPHA] = theme.backgroundAlpha
            prefs[KEY_TEXT_COLOR] = theme.textColorHex
            prefs[KEY_SHADOW_ENABLED] = theme.isShadowEnabled
        }
    }

    /**
     * 갱신 주기 저장
     */
    suspend fun saveUpdateInterval(minutes: Int) {
        context.weatherDataStore.edit { prefs ->
            prefs[KEY_UPDATE_INTERVAL] = minutes
        }
    }
}
