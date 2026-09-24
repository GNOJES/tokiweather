package com.toki.weather.data.remote

fun requireKmaItems(response: KmaResponse): List<KmaResponse.Item> {
    check(response.response.header.resultCode == "00") { "KMA request failed" }
    return response.response.body?.items?.item
        ?: throw IllegalStateException("KMA response has no items")
}

fun requireCurrentTemperature(items: List<KmaResponse.Item>): Int =
    items.firstOrNull { it.category == "T1H" }
        ?.value?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
        ?: throw IllegalStateException("KMA response has no valid current temperature")

fun forecastTemperature(items: List<KmaResponse.Item>, date: String, category: String): Int? =
    items.firstOrNull { it.category == category && it.fcstDate == date }
        ?.fcstValue?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
