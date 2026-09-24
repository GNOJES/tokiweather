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

            // 3. 필수 날씨 응답을 먼저 검증한다. 오류 응답은 캐시에 쓰지 않는다.
            val forecast = parseWeather(finalLocationName, ncstResponse, fcstResponse)

            // 4. 실시간 대기질 (미세먼지 PM10, 초미세먼지 PM2.5) 조회
            val (pm10, pm25) = airQualityRepository.fetch(
                latitude = locInfo.latitude ?: 37.5665,
                longitude = locInfo.longitude ?: 126.9780
            )

            val weather = forecast.copy(pm10 = pm10, pm25 = pm25)

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
        fcstResponse: KmaResponse
    ): CachedWeather {
        val ncstItems = requireKmaItems(ncstResponse)
        val fcstItems = requireKmaItems(fcstResponse)

        // --- 현재 기온 ---
        val currentTemp = requireCurrentTemperature(ncstItems)

        // --- 현재 날씨 상태 ---
        val currentPty = ncstItems
            .firstOrNull { it.category == "PTY" }
            ?.value?.toIntOrNull() ?: 0
        // 초단기실황에는 SKY가 없으므로, 단기예보에서 현재 시각에 가까운 SKY를 가져옴
        val todayStr = DateTimeUtils.todayString()
        val currentSky = fcstItems
            .filter { it.category == "SKY" && it.fcstDate == todayStr }
            .minByOrNull { it.fcstTime ?: "" }
            ?.fcstValue?.toIntOrNull() ?: 1
        val currentCondition = WeatherCondition.fromCodes(currentPty, currentSky)

        // --- 내일 날씨 ---
        val tomorrowStr = DateTimeUtils.tomorrowString()
        val tomorrowMin = forecastTemperature(fcstItems, tomorrowStr, "TMN")
        val tomorrowMax = forecastTemperature(fcstItems, tomorrowStr, "TMX")
        val tomorrowCondition = getDayRepresentativeCondition(fcstItems, tomorrowStr)

        // --- 모레 날씨 ---
        val dayAfterStr = DateTimeUtils.dayAfterTomorrowString()
        val dayAfterMin = forecastTemperature(fcstItems, dayAfterStr, "TMN")
        val dayAfterMax = forecastTemperature(fcstItems, dayAfterStr, "TMX")
        val dayAfterCondition = getDayRepresentativeCondition(fcstItems, dayAfterStr)

        // --- 강수확률 (POP) ---
        val currentHour = String.format("%02d00", java.time.LocalDateTime.now().hour)
        // 오늘은 현재 시간부터 오늘 중의 최대 강수확률
        val todayPopItems = fcstItems.filter {
            it.category == "POP" && it.fcstDate == todayStr && (it.fcstTime ?: "0000") >= currentHour
        }
        val todayPop = if (todayPopItems.isNotEmpty()) {
            todayPopItems.mapNotNull { it.fcstValue?.toIntOrNull() }.maxOrNull() ?: 0
        } else {
            fcstItems.filter { it.category == "POP" && it.fcstDate == todayStr }
                .mapNotNull { it.fcstValue?.toIntOrNull() }
                .maxOrNull() ?: 0
        }

        // 내일 강수확률: 내일 중 최대 강수확률
        val tomorrowPop = fcstItems.filter {
            it.category == "POP" && it.fcstDate == tomorrowStr
        }.mapNotNull { it.fcstValue?.toIntOrNull() }.maxOrNull() ?: 0

        // 모레 강수확률: 모레 중 최대 강수확률
        val dayAfterPop = fcstItems.filter {
            it.category == "POP" && it.fcstDate == dayAfterStr
        }.mapNotNull { it.fcstValue?.toIntOrNull() }.maxOrNull() ?: 0

        return CachedWeather(
            locationName = locationName,
            currentTemp = currentTemp,
            currentCondition = currentCondition,
            todayPop = todayPop,
            pm10 = -1,
            pm25 = -1,
            tomorrowMin = tomorrowMin,
            tomorrowMax = tomorrowMax,
            tomorrowCondition = tomorrowCondition,
            tomorrowPop = tomorrowPop,
            dayAfterMin = dayAfterMin,
            dayAfterMax = dayAfterMax,
            dayAfterCondition = dayAfterCondition,
            dayAfterPop = dayAfterPop
        )
    }

    /**
     * 특정 날짜의 대표 날씨 상태 결정
     * 오후 시간대(1200~1800)의 SKY/PTY 중 가장 안 좋은 상태를 대표로 사용
     */
    private fun getDayRepresentativeCondition(
        items: List<KmaResponse.Item>,
        dateStr: String
    ): WeatherCondition {
        val afternoonTimes = listOf("1200", "1300", "1400", "1500", "1600", "1700", "1800")

        val ptyItems = items.filter {
            it.category == "PTY" && it.fcstDate == dateStr && it.fcstTime in afternoonTimes
        }
        val skyItems = items.filter {
            it.category == "SKY" && it.fcstDate == dateStr && it.fcstTime in afternoonTimes
        }

        val worstPty = ptyItems
            .mapNotNull { it.fcstValue?.toIntOrNull() }
            .filter { it > 0 }
            .maxOrNull()

        if (worstPty != null && worstPty > 0) {
            return WeatherCondition.fromCodes(worstPty, 4)
        }

        val worstSky = skyItems
            .mapNotNull { it.fcstValue?.toIntOrNull() }
            .maxOrNull() ?: 1

        return WeatherCondition.fromCodes(0, worstSky)
    }
}
