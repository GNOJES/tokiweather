package com.toki.weather.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 기상청 API 응답 모델
 */
data class KmaResponse(
    val response: Response
) {
    data class Response(
        val header: Header,
        val body: Body?
    )

    data class Header(
        val resultCode: String,
        val resultMsg: String
    )

    data class Body(
        val items: Items?,
        val totalCount: Int
    )

    data class Items(
        val item: List<Item>
    )

    data class Item(
        val baseDate: String,
        val baseTime: String,
        val category: String,
        val fcstDate: String?,
        val fcstTime: String?,
        val fcstValue: String?,
        @SerializedName("obsrValue") val obsrValue: String?,
        val nx: Int,
        val ny: Int
    ) {
        /**
         * 실황 데이터(obsrValue)와 예보 데이터(fcstValue) 통합 접근
         */
        val value: String
            get() = obsrValue ?: fcstValue ?: ""
    }
}
