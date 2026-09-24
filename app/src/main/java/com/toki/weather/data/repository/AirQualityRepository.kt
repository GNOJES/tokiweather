package com.toki.weather.data.repository

import android.util.Log
import com.toki.weather.BuildConfig
import com.toki.weather.data.remote.AirKoreaResponse
import com.toki.weather.data.remote.AirQualityApiService
import com.toki.weather.data.remote.AirQualityStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/** 현재 위치에서 가장 가까운 측정소의 최신 시간별 실측값을 조회한다. */
class AirQualityRepository(
    private val api: AirQualityApiService,
    private val serviceKey: String,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val stationMutex = Mutex()
    private var cachedStations: List<AirQualityStation> = emptyList()
    private var stationsLoadedAt = 0L

    suspend fun fetch(latitude: Double, longitude: Double): Pair<Int, Int> {
        if (serviceKey.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return -1 to -1
        var phase = "stations"
        return try {
            val nearest = stations().mapNotNull { station ->
                val lat = station.dmX?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = station.dmY?.toDoubleOrNull() ?: return@mapNotNull null
                if (station.stationName.isNullOrBlank() || !lat.isFinite() || !lon.isFinite() ||
                    lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                station to distanceKm(latitude, longitude, lat, lon)
            }.minByOrNull { it.second }?.takeIf { it.second <= 50.0 }?.first
                ?: return -1 to -1
            phase = "measurement"
            val measurement = api.getAirQuality(serviceKey, nearest.stationName!!)
                .checkedItems().firstOrNull() ?: return -1 to -1
            phase = "timestamp"
            val measuredAt = measurement.dataTime?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    .atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
            } ?: return -1 to -1
            // 오래된 마지막 정상값을 현재 값으로 보여주지 않는다.
            if (now() - measuredAt !in 0L..3 * 60 * 60 * 1000L) return -1 to -1
            concentration(measurement.pm10Value, measurement.pm10Flag) to
                concentration(measurement.pm25Value, measurement.pm25Flag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 예외 메시지에는 URL과 인증키가 포함될 수 있으므로 유형만 기록한다.
            if (BuildConfig.DEBUG) runCatching { Log.w("AirQualityRepository", "$phase failed: ${e.javaClass.simpleName}") }
            -1 to -1
        }
    }

    private suspend fun stations(): List<AirQualityStation> = stationMutex.withLock {
        if (cachedStations.isNotEmpty() && now() - stationsLoadedAt in 0L until 24 * 60 * 60 * 1000L) {
            return@withLock cachedStations
        }
        val collected = mutableListOf<AirQualityStation>()
        var page = 1
        do {
            val response = api.getStations(serviceKey, page = page)
            val items = response.checkedItems()
            collected.addAll(items)
            val total = response.response?.body?.totalCount ?: 0
            if (collected.size >= total || items.isEmpty()) break
            page++
            check(page <= 20) { "Station pagination limit exceeded" }
        } while (true)
        cachedStations = collected
        stationsLoadedAt = now()
        collected
    }

    private fun <T> AirKoreaResponse<T>.checkedItems(): List<T> {
        check(response?.header?.resultCode == "00") { "AirKorea request failed" }
        return response?.body?.items.orEmpty()
    }

    private fun concentration(value: String?, flag: String?): Int =
        if (!flag.isNullOrBlank()) -1 else value?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: -1

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
