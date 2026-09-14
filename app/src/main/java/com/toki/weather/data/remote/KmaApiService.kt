package com.toki.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 기상청 단기예보 API 서비스 인터페이스
 * Base URL: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/
 */
interface KmaApiService {

    /**
     * 초단기실황 조회 - 현재 기온(T1H), 강수형태(PTY) 등
     * 매시 40분 이후 발표, 정시 base_time 사용
     */
    @GET("getUltraSrtNcst")
    suspend fun getUltraSrtNcst(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("base_date") baseDate: String,
        @Query("base_time") baseTime: String,
        @Query("nx") nx: Int,
        @Query("ny") ny: Int,
        @Query("dataType") dataType: String = "JSON",
        @Query("numOfRows") numOfRows: Int = 10
    ): KmaResponse

    /**
     * 단기예보 조회 - 기온(TMP), 최저(TMN), 최고(TMX), 하늘상태(SKY), 강수형태(PTY)
     * 하루 8회 발표: 0200, 0500, 0800, 1100, 1400, 1700, 2000, 2300
     */
    @GET("getVilageFcst")
    suspend fun getVilageFcst(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("base_date") baseDate: String,
        @Query("base_time") baseTime: String,
        @Query("nx") nx: Int,
        @Query("ny") ny: Int,
        @Query("dataType") dataType: String = "JSON",
        @Query("numOfRows") numOfRows: Int = 1000
    ): KmaResponse
}
