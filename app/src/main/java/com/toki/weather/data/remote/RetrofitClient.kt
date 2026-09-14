package com.toki.weather.data.remote

import com.toki.weather.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 싱글턴 클라이언트
 */
object RetrofitClient {

    private const val BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private const val AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"

    private val airQualityRetrofit = Retrofit.Builder()
        .baseUrl(AIR_QUALITY_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val kmaApiService: KmaApiService = retrofit.create(KmaApiService::class.java)
    val airQualityApiService: AirQualityApiService = airQualityRetrofit.create(AirQualityApiService::class.java)
}
