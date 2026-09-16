package com.toki.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface AirQualityApiService {

    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "pm10,pm2_5",
        @Query("timezone") timezone: String = "Asia/Seoul"
    ): AirQualityResponse
}
