package com.toki.weather.data.repository

import android.content.Context
import android.util.Log
import com.toki.weather.BuildConfig
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.remote.KmaResponse
import com.toki.weather.data.remote.RetrofitClient
import com.toki.weather.util.DateTimeUtils
import com.toki.weather.util.LocationHelper

/**
 * 날씨 데이터 Repository
 * 위치 조회(GPS) → 기상청 API 호출 → 데이터 가공 → DataStore 캐싱
 */
class WeatherRepository(private val context: Context) {

    private val api = RetrofitClient.kmaApiService
    private val airQualityApi = RetrofitClient.airQualityApiService
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
            Log.d(TAG, "Current location: ${locInfo.locationName} (nx=${locInfo.nx}, ny=${locInfo.ny})")

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

            // 3. 실시간 대기질 (미세먼지 PM10, 초미세먼지 PM2.5) 조회
            val (pm10, pm25) = fetchAirQuality(
                lat = locInfo.latitude ?: 37.5665,
                lon = locInfo.longitude ?: 126.9780
            )

            // 4. 데이터 가공
            val weather = parseWeather(locInfo.locationName, ncstResponse, fcstResponse, pm10, pm25)

            // 5. 캐시 저장
            dataStore.save(weather)

            Log.d(TAG, "Weather updated: $weather")
            Result.success(weather)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather", e)
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
        pm10: Int = -1,
        pm25: Int = -1
    ): CachedWeather {
        val ncstItems = ncstResponse.response.body?.items?.item ?: emptyList()
        val fcstItems = fcstResponse.response.body?.items?.item ?: emptyList()

        // --- 현재 기온 ---
        val currentTemp = ncstItems
            .firstOrNull { it.category == "T1H" }
            ?.value?.toDoubleOrNull()?.toInt() ?: 0

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
        val tomorrowMin = fcstItems
            .firstOrNull { it.category == "TMN" && it.fcstDate == tomorrowStr }
            ?.fcstValue?.toDoubleOrNull()?.toInt() ?: 0
        val tomorrowMax = fcstItems
            .firstOrNull { it.category == "TMX" && it.fcstDate == tomorrowStr }
            ?.fcstValue?.toDoubleOrNull()?.toInt() ?: 0
        val tomorrowCondition = getDayRepresentativeCondition(fcstItems, tomorrowStr)

        // --- 모레 날씨 ---
        val dayAfterStr = DateTimeUtils.dayAfterTomorrowString()
        val dayAfterMin = fcstItems
            .firstOrNull { it.category == "TMN" && it.fcstDate == dayAfterStr }
            ?.fcstValue?.toDoubleOrNull()?.toInt() ?: 0
        val dayAfterMax = fcstItems
            .firstOrNull { it.category == "TMX" && it.fcstDate == dayAfterStr }
            ?.fcstValue?.toDoubleOrNull()?.toInt() ?: 0
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
            pm10 = pm10,
            pm25 = pm25,
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
     * Open-Meteo Air Quality API를 호출하여 미세먼지(PM10) 및 초미세먼지(PM2.5) 수치 획득
     */
    private suspend fun fetchAirQuality(lat: Double, lon: Double): Pair<Int, Int> {
        return try {
            val response = airQualityApi.getAirQuality(latitude = lat, longitude = lon)
            val pm10 = response.current?.pm10?.toInt() ?: -1
            val pm25 = response.current?.pm25?.toInt() ?: -1
            Log.d(TAG, "Air quality fetched: PM10=$pm10, PM2.5=$pm25")
            Pair(pm10, pm25)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch air quality, fallback to -1", e)
            Pair(-1, -1)
        }
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
