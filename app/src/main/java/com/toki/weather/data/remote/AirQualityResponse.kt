package com.toki.weather.data.remote

// 에어코리아 두 서비스가 사용하는 공통 JSON envelope.
data class AirKoreaResponse<T>(val response: AirKoreaEnvelope<T>? = null)
data class AirKoreaEnvelope<T>(val header: AirKoreaHeader? = null, val body: AirKoreaBody<T>? = null)
data class AirKoreaHeader(val resultCode: String? = null)
data class AirKoreaBody<T>(val items: List<T>? = null, val totalCount: Int = 0)

data class AirQualityStation(
    val stationName: String? = null,
    // API의 dmX는 위도, dmY는 경도 (WGS84).
    val dmX: String? = null,
    val dmY: String? = null
)

data class AirQualityMeasurement(
    val dataTime: String? = null,
    val pm10Value: String? = null,
    val pm25Value: String? = null,
    val pm10Flag: String? = null,
    val pm25Flag: String? = null
)
