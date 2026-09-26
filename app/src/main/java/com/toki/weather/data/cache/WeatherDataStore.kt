package com.toki.weather.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.HalfDayForecast
import com.toki.weather.data.model.HourlyForecast
import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.model.WidgetThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
        private val KEY_CURRENT_HUMIDITY = intPreferencesKey("current_humidity")
        private val KEY_TODAY_POP = intPreferencesKey("today_pop")
        private val KEY_HOURLY_FORECASTS = stringPreferencesKey("hourly_forecasts")
        private val KEY_HOURLY_FORECAST_ISSUED_AT = stringPreferencesKey("hourly_forecast_issued_at")
        private val KEY_HALF_DAY_FORECASTS = stringPreferencesKey("half_day_forecasts")
        // 출처 전환 이전 Open-Meteo 캐시는 읽지 않는다.
        private val KEY_PM10 = intPreferencesKey("airkorea_pm10")
        private val KEY_PM25 = intPreferencesKey("airkorea_pm25")
        private val KEY_PM_OBSERVED_AT = longPreferencesKey("airkorea_observed_at")
        private val KEY_AIR_QUALITY_LATITUDE = doublePreferencesKey("airkorea_latitude")
        private val KEY_AIR_QUALITY_LONGITUDE = doublePreferencesKey("airkorea_longitude")
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
        private val KEY_HOURLY_INTERVAL = intPreferencesKey("hourly_forecast_interval_hours")

        // 사용자 직접 지정 동네 이름 키 (비어있으면 GPS 자동 감지 사용)
        private val KEY_CUSTOM_LOCATION_NAME = stringPreferencesKey("custom_location_name")
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
            currentHumidity = prefs[KEY_CURRENT_HUMIDITY],
            todayPop = prefs[KEY_TODAY_POP] ?: 0,
            hourlyForecasts = prefs[KEY_HOURLY_FORECASTS]?.let { json ->
                runCatching {
                    Gson().fromJson<List<HourlyForecast>>(json,
                        object : TypeToken<List<HourlyForecast>>() {}.type)
                }.getOrNull()
            }.orEmpty(),
            hourlyForecastIssuedAt = prefs[KEY_HOURLY_FORECAST_ISSUED_AT],
            halfDayForecasts = prefs[KEY_HALF_DAY_FORECASTS]?.let { json ->
                runCatching {
                    Gson().fromJson<List<HalfDayForecast?>>(json,
                        object : TypeToken<List<HalfDayForecast?>>() {}.type)
                }.getOrNull()
            }.orEmpty(),
            pm10 = prefs[KEY_PM10] ?: -1,
            pm25 = prefs[KEY_PM25] ?: -1,
            pmObservedAt = prefs[KEY_PM_OBSERVED_AT],
            airQualityLatitude = prefs[KEY_AIR_QUALITY_LATITUDE],
            airQualityLongitude = prefs[KEY_AIR_QUALITY_LONGITUDE],
            tomorrowMin = prefs[KEY_TOMORROW_MIN],
            tomorrowMax = prefs[KEY_TOMORROW_MAX],
            tomorrowCondition = prefs[KEY_TOMORROW_CONDITION]?.let {
                try { WeatherCondition.valueOf(it) } catch (_: Exception) { WeatherCondition.UNKNOWN }
            } ?: WeatherCondition.UNKNOWN,
            tomorrowPop = prefs[KEY_TOMORROW_POP] ?: 0,
            dayAfterMin = prefs[KEY_DAY_AFTER_MIN],
            dayAfterMax = prefs[KEY_DAY_AFTER_MAX],
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

    val hourlyIntervalFlow: Flow<Int> = context.weatherDataStore.data.map { prefs ->
        prefs[KEY_HOURLY_INTERVAL]?.takeIf { it == 1 || it == 3 } ?: 1
    }

    /**
     * 날씨 데이터 저장
     */
    suspend fun save(weather: CachedWeather) {
        context.weatherDataStore.edit { prefs ->
            prefs[KEY_LOCATION_NAME] = weather.locationName
            prefs[KEY_CURRENT_TEMP] = weather.currentTemp
            prefs[KEY_CURRENT_CONDITION] = weather.currentCondition.name
            weather.currentHumidity?.let { prefs[KEY_CURRENT_HUMIDITY] = it } ?: prefs.remove(KEY_CURRENT_HUMIDITY)
            prefs[KEY_TODAY_POP] = weather.todayPop
            prefs[KEY_HOURLY_FORECASTS] = Gson().toJson(weather.hourlyForecasts)
            weather.hourlyForecastIssuedAt?.let { prefs[KEY_HOURLY_FORECAST_ISSUED_AT] = it } ?: prefs.remove(KEY_HOURLY_FORECAST_ISSUED_AT)
            prefs[KEY_HALF_DAY_FORECASTS] = Gson().toJson(weather.halfDayForecasts)
            prefs[KEY_PM10] = weather.pm10
            prefs[KEY_PM25] = weather.pm25
            weather.pmObservedAt?.let { prefs[KEY_PM_OBSERVED_AT] = it } ?: prefs.remove(KEY_PM_OBSERVED_AT)
            weather.airQualityLatitude?.let { prefs[KEY_AIR_QUALITY_LATITUDE] = it } ?: prefs.remove(KEY_AIR_QUALITY_LATITUDE)
            weather.airQualityLongitude?.let { prefs[KEY_AIR_QUALITY_LONGITUDE] = it } ?: prefs.remove(KEY_AIR_QUALITY_LONGITUDE)
            weather.tomorrowMin?.let { prefs[KEY_TOMORROW_MIN] = it } ?: prefs.remove(KEY_TOMORROW_MIN)
            weather.tomorrowMax?.let { prefs[KEY_TOMORROW_MAX] = it } ?: prefs.remove(KEY_TOMORROW_MAX)
            prefs[KEY_TOMORROW_CONDITION] = weather.tomorrowCondition.name
            prefs[KEY_TOMORROW_POP] = weather.tomorrowPop
            weather.dayAfterMin?.let { prefs[KEY_DAY_AFTER_MIN] = it } ?: prefs.remove(KEY_DAY_AFTER_MIN)
            weather.dayAfterMax?.let { prefs[KEY_DAY_AFTER_MAX] = it } ?: prefs.remove(KEY_DAY_AFTER_MAX)
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

    suspend fun saveHourlyInterval(hours: Int) {
        require(hours == 1 || hours == 3)
        context.weatherDataStore.edit { prefs -> prefs[KEY_HOURLY_INTERVAL] = hours }
    }

    /**
     * 사용자 직접 지정 동네 이름 Flow
     */
    val customLocationNameFlow: Flow<String> = context.weatherDataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_LOCATION_NAME] ?: ""
    }

    /**
     * 사용자 직접 지정 동네 이름 저장 (빈 문자열이면 GPS 자동 감지로 복원)
     */
    suspend fun saveCustomLocationName(name: String) {
        context.weatherDataStore.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(KEY_CUSTOM_LOCATION_NAME)
            } else {
                prefs[KEY_CUSTOM_LOCATION_NAME] = name.trim()
            }
        }
    }
}
