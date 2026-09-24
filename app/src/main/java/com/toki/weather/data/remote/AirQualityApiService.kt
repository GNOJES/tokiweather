package com.toki.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface AirQualityApiService {
    @GET("MsrstnInfoInqireSvc/getMsrstnList")
    suspend fun getStations(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") page: Int = 1,
        @Query("numOfRows") pageSize: Int = 1000,
        @Query("returnType") returnType: String = "json"
    ): AirKoreaResponse<AirQualityStation>

    @GET("ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty")
    suspend fun getAirQuality(
        @Query("serviceKey") serviceKey: String,
        @Query("stationName") stationName: String,
        @Query("dataTerm") dataTerm: String = "DAILY",
        @Query("ver") version: String = "1.3",
        @Query("numOfRows") pageSize: Int = 1,
        @Query("pageNo") page: Int = 1,
        @Query("returnType") returnType: String = "json"
    ): AirKoreaResponse<AirQualityMeasurement>
}
