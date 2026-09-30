package com.toki.weather.data.remote

/** totalCount까지 모든 페이지를 읽는다. 중간 실패 시 불완전한 예보를 반환하지 않는다. */
suspend fun fetchAllKmaItems(fetchPage: suspend (Int) -> KmaResponse): List<KmaResponse.Item> {
    val items = mutableListOf<KmaResponse.Item>()
    var pageNumber = 1
    var totalCount: Int? = null
    do {
        val response = fetchPage(pageNumber)
        val pageItems = requireKmaItems(response)
        val pageTotal = response.response.body!!.totalCount
        if (totalCount == null) totalCount = pageTotal
        check(pageTotal == totalCount && pageItems.isNotEmpty()) { "KMA forecast page is incomplete" }
        check(pageItems.none { it in items }) { "KMA forecast page is repeated" }
        items.addAll(pageItems)
        check(items.size <= totalCount) { "KMA forecast item count is inconsistent" }
        pageNumber++
    } while (items.size < totalCount!!)
    return items
}

fun requireKmaItems(response: KmaResponse): List<KmaResponse.Item> {
    check(response.response.header.resultCode == "00") { "KMA request failed" }
    return response.response.body?.items?.item
        ?: throw IllegalStateException("KMA response has no items")
}

fun requireCurrentTemperature(items: List<KmaResponse.Item>): Int =
    items.firstOrNull { it.category == "T1H" }
        ?.value?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
        ?: throw IllegalStateException("KMA response has no valid current temperature")

fun currentHumidity(items: List<KmaResponse.Item>): Int? =
    items.firstOrNull { it.category == "REH" }
        ?.value?.toIntOrNull()?.takeIf { it in 0..100 }

fun forecastTemperature(items: List<KmaResponse.Item>, date: String, category: String): Int? =
    items.firstOrNull { it.category == category && it.fcstDate == date }
        ?.fcstValue?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
