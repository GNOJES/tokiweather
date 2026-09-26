package com.toki.weather.data.repository

import android.content.Context
import android.util.Log
import com.toki.weather.BuildConfig
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.remote.KmaResponse
import com.toki.weather.data.remote.RetrofitClient
import com.toki.weather.data.remote.forecastTemperature
import com.toki.weather.data.remote.currentHumidity
import com.toki.weather.data.remote.requireCurrentTemperature
import com.toki.weather.data.remote.requireKmaItems
import com.toki.weather.util.DateTimeUtils
import com.toki.weather.util.LocationHelper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull

/**
 * 날씨 데이터 Repository
 * 위치 조회(GPS) → 기상청 API 호출 → 데이터 가공 → DataStore 캐싱
 */
class WeatherRepository(private val context: Context) {

    private val api = RetrofitClient.kmaApiService
    private val airQualityRepository = RetrofitClient.airQualityRepository
    private val dataStore = WeatherDataStore(context)
    private val serviceKey: String
        get() {
            val raw = BuildConfig.KMA_API_KEY
            return if (raw.contains("%")) {
                raw
            } else {
                try {
                    java.net.URLEncoder.encode(raw, "UTF-8")
                } catch (_: Exception) {
                    raw
                }
            }
        }

    companion object {
        private const val TAG = "WeatherRepository"
    }

    /**
     * API에서 날씨 및 대기질 데이터를 가져와 캐시에 저장
     */
    suspend fun fetchAndSave(): Result<CachedWeather> {
        return try {
            // 0. GPS 기반 위치 및 지역 이름 획득
            val locInfo = LocationHelper.getCurrentLocationInfo(context)
            val customName = try {
                dataStore.customLocationNameFlow.firstOrNull()?.trim() ?: ""
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { "" }
            val finalLocationName = if (customName.isNotBlank()) customName else locInfo.locationName
            Log.d(TAG, "Current location: $finalLocationName (GPS: ${locInfo.locationName}, custom: $customName, nx=${locInfo.nx}, ny=${locInfo.ny})")

            // 1. 현재 기온 (초단기실황)
            val (ncstDate, ncstTime) = DateTimeUtils.getUltraSrtNcstBaseDateTime()
            val ncstResponse = api.getUltraSrtNcst(
                serviceKey = serviceKey,
                baseDate = ncstDate,
                baseTime = ncstTime,
                nx = locInfo.nx,
                ny = locInfo.ny
            )

            // 2. 단기예보 (내일/모레 TMN, TMX, SKY, PTY)
            val (fcstDate, fcstTime) = DateTimeUtils.getVilageFcstBaseDateTime()
            val fcstResponse = api.getVilageFcst(
                serviceKey = serviceKey,
                baseDate = fcstDate,
                baseTime = fcstTime,
                nx = locInfo.nx,
                ny = locInfo.ny
            )

            // 초단기예보는 가까운 6시간의 날씨와 강수확률을 보완한다.
            // 일시적으로 실패해도 단기예보만으로 갱신을 계속한다.
            val ultraNow = java.time.LocalDateTime.now()
            var ultraFcstResponse: KmaResponse? = null
            for (candidate in listOf(ultraNow, ultraNow.minusHours(1))) {
                val (ultraDate, ultraTime) = DateTimeUtils.getUltraSrtFcstBaseDateTime(candidate)
                try {
                    val response = api.getUltraSrtFcst(
                        serviceKey = serviceKey,
                        baseDate = ultraDate,
                        baseTime = ultraTime,
                        nx = locInfo.nx,
                        ny = locInfo.ny
                    )
                    requireKmaItems(response)
                    ultraFcstResponse = response
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Ultra-short forecast unavailable for $ultraTime: ${e.javaClass.simpleName}")
                }
            }

            // 3. 필수 날씨 응답을 먼저 검증한다. 오류 응답은 캐시에 쓰지 않는다.
            val forecast = parseWeather(finalLocationName, ncstResponse, fcstResponse, ultraFcstResponse)

            // 4. 실시간 대기질 (미세먼지 PM10, 초미세먼지 PM2.5) 조회
            val airLatitude = locInfo.latitude ?: 37.5665
            val airLongitude = locInfo.longitude ?: 126.9780
            val reading = airQualityRepository.fetchDetailed(
                latitude = airLatitude,
                longitude = airLongitude
            )
            val weather = mergeAirQuality(
                forecast.copy(airQualityLatitude = airLatitude, airQualityLongitude = airLongitude),
                reading,
                dataStore.weatherFlow.firstOrNull(),
                System.currentTimeMillis()
            )

            // 5. 캐시 저장
            dataStore.save(weather)

            Log.d(TAG, "Weather updated: $weather")
            Result.success(weather)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * API 응답에서 위젯에 필요한 데이터 추출
     */
    private fun parseWeather(
        locationName: String,
        ncstResponse: KmaResponse,
        fcstResponse: KmaResponse,
        ultraFcstResponse: KmaResponse?
    ): CachedWeather {
        val ncstItems = requireKmaItems(ncstResponse)
        val fcstItems = requireKmaItems(fcstResponse)

        // --- 현재 기온 ---
        val currentTemp = requireCurrentTemperature(ncstItems)
        val humidity = currentHumidity(ncstItems)

        // --- 현재 날씨 상태 ---
        val currentPty = ncstItems
            .firstOrNull { it.category == "PTY" }
            ?.value?.toIntOrNull() ?: 0
        // 초단기실황에는 SKY/POP가 없으므로 현재 시간대의 단기예보를 사용한다.
        val todayStr = DateTimeUtils.todayString()
        val currentHour = String.format("%02d00", java.time.LocalDateTime.now().hour)
        val currentForecast = selectCurrentForecastMoment(fcstItems, todayStr, currentHour)
        val currentCondition = WeatherCondition.fromCodes(currentPty, currentForecast.sky ?: -1)
        val todayForecast = selectDailyForecast(fcstItems, todayStr, currentHour)
        val shortTermHourly = selectHourlyForecast(fcstItems, todayStr, currentHour)
        val ultraItems = ultraFcstResponse?.let { response ->
            runCatching { requireKmaItems(response) }.getOrNull()
        }.orEmpty()
        val hourlyForecasts = mergeUltraShortForecast(shortTermHourly, ultraItems)
        val hourlyIssuedAt = if (ultraItems.isNotEmpty()) {
            ultraItems.first().baseTime
        } else fcstItems.firstOrNull()?.baseTime

        // --- 내일 날씨 ---
        val tomorrowStr = DateTimeUtils.tomorrowString()
        val tomorrowMin = forecastTemperature(fcstItems, tomorrowStr, "TMN")
        val tomorrowMax = forecastTemperature(fcstItems, tomorrowStr, "TMX")
        val tomorrowForecast = selectDailyForecast(fcstItems, tomorrowStr, "0000")

        // --- 모레 날씨 ---
        val dayAfterStr = DateTimeUtils.dayAfterTomorrowString()
        val dayAfterMin = forecastTemperature(fcstItems, dayAfterStr, "TMN")
        val dayAfterMax = forecastTemperature(fcstItems, dayAfterStr, "TMX")
        val dayAfterForecast = selectDailyForecast(fcstItems, dayAfterStr, "0000")

        val halfDayForecasts = listOf(todayStr, tomorrowStr, dayAfterStr).flatMap { date ->
            listOf(
                selectHalfDayForecast(fcstItems, date, "0600", "1200"),
                selectHalfDayForecast(fcstItems, date, "1200", "1800")
            )
        }

        return CachedWeather(
            locationName = locationName,
            currentTemp = currentTemp,
            currentCondition = currentCondition,
            currentHumidity = humidity,
            todayPop = hourlyForecasts.filter { it.date == todayStr }
                .mapNotNull { it.pop }.maxOrNull() ?: todayForecast.pop,
            hourlyForecasts = hourlyForecasts,
            hourlyForecastIssuedAt = hourlyIssuedAt?.takeIf { it.length == 4 }
                ?.let { "${it.take(2)}:${it.takeLast(2)}" },
            halfDayForecasts = halfDayForecasts,
            pm10 = -1,
            pm25 = -1,
            tomorrowMin = tomorrowMin,
            tomorrowMax = tomorrowMax,
            tomorrowCondition = tomorrowForecast.condition,
            tomorrowPop = tomorrowForecast.pop,
            dayAfterMin = dayAfterMin,
            dayAfterMax = dayAfterMax,
            dayAfterCondition = dayAfterForecast.condition,
            dayAfterPop = dayAfterForecast.pop
        )
    }
}
