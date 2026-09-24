package com.toki.weather.data.remote

import com.toki.weather.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 싱글턴 클라이언트
 */
object RetrofitClient {

    private const val BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private const val AIR_QUALITY_BASE_URL = "https://apis.data.go.kr/B552584/"

    // 인증키가 쿼리에 포함되므로 대기질 요청에는 HTTP 로깅을 사용하지 않는다.
    private val airQualityHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val airQualityRetrofit = Retrofit.Builder()
        .baseUrl(AIR_QUALITY_BASE_URL)
        .client(airQualityHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val airQualityRepository by lazy {
        com.toki.weather.data.repository.AirQualityRepository(airQualityApiService, BuildConfig.AIRKOREA_API_KEY)
    }

    val kmaApiService: KmaApiService = retrofit.create(KmaApiService::class.java)
    val airQualityApiService: AirQualityApiService = airQualityRetrofit.create(AirQualityApiService::class.java)
}
