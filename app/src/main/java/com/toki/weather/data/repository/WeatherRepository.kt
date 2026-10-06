package com.toki.weather.data.repository

import android.content.Context
import android.util.Log
import com.toki.weather.BuildConfig
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import java.time.ZoneId
import com.toki.weather.data.remote.KmaResponse
import com.toki.weather.data.remote.RetrofitClient
import com.toki.weather.data.remote.forecastTemperature
import com.toki.weather.data.remote.fetchAllKmaItems
import com.toki.weather.data.remote.currentHumidity
import com.toki.weather.data.remote.requireCurrentTemperature
import com.toki.weather.data.remote.requireKmaItems
import com.toki.weather.util.DateTimeUtils
import com.toki.weather.util.LocationHelper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    suspend fun fetchAndSave(
        allowSavedLocation: Boolean = false,
        source: RefreshSource = if (allowSavedLocation) RefreshSource.AUTOMATIC else RefreshSource.MANUAL,
        onWeatherSaved: (suspend () -> Unit)? = null
    ): Result<CachedWeather> {
        val trace = RefreshTrace(dataStore, dataStore.beginRequest(), source)
        val start = System.nanoTime()
        var weatherSaved = false
        trace.event(RefreshStage.REQUEST, RefreshState.STARTED)
        return try {
            val completed = withTimeoutOrNull(40_000L) {
                val forecast = withTimeout(25_000L) { fetchForecast(trace, allowSavedLocation) }
                val accepted = publishWeatherThenAir(forecast,
                    publish = { value ->
                        val saveStart = System.nanoTime()
                        trace.event(RefreshStage.WEATHER_SAVE, RefreshState.STARTED)
                        val saved = dataStore.commit(trace.id) { previous ->
                            mergeAirQuality(value, AirQualityReading(-1, -1, null, failedToLoad = true),
                                previous, System.currentTimeMillis()).let {
                                it.copy(refresh = it.refresh.copy(airQualityPending = true))
                            }
                        }
                        weatherSaved = saved
                        trace.event(RefreshStage.WEATHER_SAVE,
                            if (saved) RefreshState.SUCCESS else RefreshState.SUPERSEDED,
                            (System.nanoTime() - saveStart) / 1_000_000)
                        saved
                    },
                    onWeatherPublished = {
                        // A display error must not invalidate a successful weather commit.
                        try {
                            val callback = onWeatherSaved
                            if (callback != null) {
                                val requested = withTimeoutOrNull(5_000L) { callback(); true } ?: false
                                trace.event(RefreshStage.WIDGET,
                                    if (requested) RefreshState.REQUESTED else RefreshState.TIMEOUT)
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { trace.event(RefreshStage.WIDGET, RefreshState.FAILED) }
                    },
                    fetchAir = {
                        trace.event(RefreshStage.AIR_QUALITY, RefreshState.STARTED)
                        airQualityRepository.fetchDetailed(forecast.refresh.latitude!!, forecast.refresh.longitude!!) {
                            phase, state, elapsed -> trace.event(phase, state, elapsed)
                        }
                    },
                    finishAir = { reading ->
                        trace.event(RefreshStage.AIR_QUALITY, when {
                            reading.timedOut -> RefreshState.TIMEOUT
                            reading.failedToLoad -> RefreshState.FAILED
                            reading.pm10 < 0 && reading.pm25 < 0 -> RefreshState.MISSING
                            else -> RefreshState.SUCCESS
                        })
                        val saveStart = System.nanoTime()
                        trace.event(RefreshStage.AIR_SAVE, RefreshState.STARTED)
                        var retained = false
                        val saved = dataStore.commit(trace.id, onlyIfCurrent = true) { current ->
                            // Only this request's weather and coordinates may receive its air result.
                            mergeAirQuality(current.copy(airQualityLatitude = current.refresh.latitude,
                                airQualityLongitude = current.refresh.longitude), reading, current,
                                System.currentTimeMillis()).also { retained = it.refresh.airQualityRetained }
                        }
                        trace.event(RefreshStage.AIR_SAVE, when {
                            !saved -> RefreshState.SUPERSEDED
                            retained -> RefreshState.RETAINED
                            else -> RefreshState.SUCCESS
                        }, (System.nanoTime() - saveStart) / 1_000_000)
                    })
                trace.event(RefreshStage.REQUEST, if (accepted) RefreshState.SUCCESS else RefreshState.SUPERSEDED,
                    (System.nanoTime() - start) / 1_000_000)
                Result.success(dataStore.weatherFlow.firstOrNull() ?: forecast)
            }
            completed ?: run {
                clearPendingAir(trace.id)
                trace.event(RefreshStage.REQUEST, RefreshState.TIMEOUT, (System.nanoTime() - start) / 1_000_000)
                if (weatherSaved) Result.success(dataStore.weatherFlow.firstOrNull()!!) else Result.failure(RefreshDeadlineException())
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                clearPendingAir(trace.id)
                trace.event(RefreshStage.REQUEST, if (e is TimeoutCancellationException) RefreshState.TIMEOUT else RefreshState.CANCELLED,
                    (System.nanoTime() - start) / 1_000_000)
            }
            // The local weather deadline is a refresh failure, caller cancellation still propagates.
            if (e is TimeoutCancellationException) Result.failure(RefreshDeadlineException()) else throw e
        } catch (e: Exception) {
            clearPendingAir(trace.id)
            trace.event(RefreshStage.REQUEST, if (weatherSaved) RefreshState.SUCCESS else RefreshState.FAILED, (System.nanoTime() - start) / 1_000_000)
            Log.e(TAG, "Weather refresh failed: ${e.javaClass.simpleName}")
            if (weatherSaved) Result.success(dataStore.weatherFlow.firstOrNull()!!) else Result.failure(e)
        }
    }

    private suspend fun clearPendingAir(id: Long) {
        dataStore.commit(id, onlyIfCurrent = true) { current ->
            mergeAirQuality(current.copy(airQualityLatitude = current.refresh.latitude,
                airQualityLongitude = current.refresh.longitude),
                AirQualityReading(-1, -1, null, failedToLoad = true), current, System.currentTimeMillis())
        }
    }

    private suspend fun fetchForecast(trace: RefreshTrace, allowSavedLocation: Boolean): CachedWeather {
        var usedSavedLocation = false
        val locInfo = trace.stage(RefreshStage.LOCATION) {
            resolveWeatherRefreshLocation(allowSavedLocation,
                currentLocation = { LocationHelper.getCurrentLocationInfo(context) },
                savedLocation = {
                    savedWeatherLocation(dataStore.weatherFlow.firstOrNull())?.also { usedSavedLocation = true }
                })
        }
        locInfo.addressSelection?.let {
            dataStore.saveAddressDiagnostics(com.toki.weather.util.AddressDiagnostics(
                locInfo.confirmedAt ?: System.currentTimeMillis(), it))
        }
        if (usedSavedLocation) trace.event(RefreshStage.LOCATION, RefreshState.RETAINED)
        val customName = dataStore.customLocationNameFlow.firstOrNull()?.trim().orEmpty()
        val name = customName.ifBlank { locInfo.locationName }
        val latitude = requireNotNull(locInfo.latitude)
        val longitude = requireNotNull(locInfo.longitude)
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val (ncstDate, ncstTime) = DateTimeUtils.getUltraSrtNcstBaseDateTime(now)
        val ncst = trace.stage(RefreshStage.OBSERVATION) {
            api.getUltraSrtNcst(serviceKey = serviceKey, baseDate = ncstDate, baseTime = ncstTime,
                nx = locInfo.nx, ny = locInfo.ny).also { requireCurrentTemperature(requireKmaItems(it)) }
        }
        val (date, time) = DateTimeUtils.getVilageFcstBaseDateTime(now)
        val latest = trace.stage(RefreshStage.SHORT_FORECAST) { fetchVilageItems(date, time, locInfo.nx, locInfo.ny) }
        val today = now.format(DateTimeFormatter.BASIC_ISO_DATE)
        val hour = "%02d00".format(now.hour)
        val previous = if (selectCurrentForecastMoment(latest, today, hour).sky == null) {
            val (oldDate, oldTime) = DateTimeUtils.getPreviousVilageFcstBaseDateTime(now)
            try {
                withTimeoutOrNull(4_000L) {
                    trace.stage(RefreshStage.PREVIOUS_FORECAST) { fetchVilageItems(oldDate, oldTime, locInfo.nx, locInfo.ny) }
                }.orEmpty()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        } else emptyList()
        var ultra: KmaResponse? = null
        withTimeoutOrNull(4_000L) {
            for (candidate in listOf(now, now.minusHours(1))) {
                val (ultraDate, ultraTime) = DateTimeUtils.getUltraSrtFcstBaseDateTime(candidate)
                try {
                    ultra = trace.stage(RefreshStage.ULTRA_FORECAST) {
                        api.getUltraSrtFcst(serviceKey = serviceKey, baseDate = ultraDate, baseTime = ultraTime,
                            nx = locInfo.nx, ny = locInfo.ny).also { requireKmaItems(it) }
                    }
                    break
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* required short-term forecast remains usable */ }
            }
        }
        val observedAt = runCatching {
            val item = requireKmaItems(ncst).first()
            LocalDateTime.parse(item.baseDate + item.baseTime, DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                .atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        }.getOrNull()
        return parseWeather(name, ncst, latest, previous, ultra, now).copy(
            airQualityLatitude = latitude, airQualityLongitude = longitude,
            refresh = RefreshMetadata(requestId = trace.id, latitude = latitude, longitude = longitude,
                locationConfirmedAt = locInfo.confirmedAt, weatherObservedAt = observedAt,
                usedSavedLocation = usedSavedLocation))
    }

    private suspend fun fetchVilageItems(date: String, time: String, nx: Int, ny: Int) =
        fetchAllKmaItems { page ->
            api.getVilageFcst(
                serviceKey = serviceKey, baseDate = date, baseTime = time,
                nx = nx, ny = ny, pageNo = page
            )
        }

    /** API 응답에서 위젯에 필요한 데이터 추출 */
    private fun parseWeather(
        locationName: String,
        ncstResponse: KmaResponse,
        fcstItems: List<KmaResponse.Item>,
        previousIssueItems: List<KmaResponse.Item>,
        ultraFcstResponse: KmaResponse?,
        now: LocalDateTime
    ): CachedWeather {
        val ncstItems = requireKmaItems(ncstResponse)

        // --- 현재 기온 ---
        val currentTemp = requireCurrentTemperature(ncstItems)
        val humidity = currentHumidity(ncstItems)
        val observedTemperature = ncstItems.firstOrNull { it.category == "T1H" }?.value?.toDoubleOrNull()
        val windSpeed = ncstItems.firstOrNull { it.category == "WSD" }?.value?.toDoubleOrNull()
        val feelsLike = apparentTemperatureCelsius(observedTemperature, humidity, windSpeed)

        // --- 현재 날씨 상태 ---
        val currentPty = ncstItems
            .firstOrNull { it.category == "PTY" }
            ?.value?.toIntOrNull() ?: 0
        // 초단기실황에는 SKY/POP가 없으므로 현재 시간대의 단기예보를 사용한다.
        val todayStr = now.format(DateTimeFormatter.BASIC_ISO_DATE)
        val currentHour = String.format("%02d00", now.hour)
        val currentForecast = selectCurrentForecastMoment(fcstItems, todayStr, currentHour, previousIssueItems)
        val currentCondition = requireResolvedCurrentCondition(currentPty, currentForecast.sky)
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
        val tomorrowStr = now.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
        val tomorrowMin = forecastTemperature(fcstItems, tomorrowStr, "TMN")
        val tomorrowMax = forecastTemperature(fcstItems, tomorrowStr, "TMX")
        val tomorrowForecast = selectDailyForecast(fcstItems, tomorrowStr, "0000")

        // --- 모레 날씨 ---
        val dayAfterStr = now.plusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE)
        val dayAfterMin = forecastTemperature(fcstItems, dayAfterStr, "TMN")
        val dayAfterMax = forecastTemperature(fcstItems, dayAfterStr, "TMX")
        val dayAfterForecast = selectDailyForecast(fcstItems, dayAfterStr, "0000")

        val halfDayForecasts = listOf(todayStr, tomorrowStr, dayAfterStr).flatMap { date ->
            val (morning, afternoon) = selectDailyHalfDays(fcstItems, date)
            listOf(morning, afternoon)
        }

        return CachedWeather(
            locationName = locationName,
            currentTemp = currentTemp,
            currentCondition = currentCondition,
            currentHumidity = humidity,
            currentFeelsLike = feelsLike,
            todayPop = hourlyForecasts.filter { it.date == todayStr }
                .mapNotNull { it.pop }.maxOrNull() ?: todayForecast.pop,
            hourlyForecasts = hourlyForecasts,
            hourlyForecastIssuedAt = hourlyIssuedAt?.takeIf { it.length == 4 }
                ?.let { "${it.take(2)}:${it.takeLast(2)}" },
            datedForecasts = fcstItems.mapNotNull { it.fcstDate }.distinct().sorted().map { date ->
                val (morning, afternoon) = selectDailyHalfDays(fcstItems, date)
                val daily = selectDailyForecast(fcstItems, date, "0000")
                DatedForecast(date, morning, afternoon,
                    forecastTemperature(fcstItems, date, "TMN") ?: morning?.minTemp,
                    forecastTemperature(fcstItems, date, "TMX") ?: afternoon?.maxTemp,
                    daily.condition, daily.pop)
            },
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

class RefreshDeadlineException : Exception("Refresh deadline exceeded")
