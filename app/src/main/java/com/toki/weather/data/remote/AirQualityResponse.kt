package com.toki.weather.data.remote

import com.google.gson.annotations.SerializedName

data class AirQualityResponse(
    @SerializedName("current")
    val current: AirQualityCurrent? = null
)

data class AirQualityCurrent(
    @SerializedName("pm10")
    val pm10: Float? = null,
    @SerializedName("pm2_5")
    val pm25: Float? = null
)
