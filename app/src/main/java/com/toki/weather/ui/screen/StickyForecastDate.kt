package com.toki.weather.ui.screen

data class StickyForecastDate(val date: String)

/** 날짜 글자를 첫 예보 칸 중앙에 고정하고 다음 날짜 글자가 겹칠 때 교체한다. */
fun stickyForecastDate(
    dates: List<String>,
    scrollPx: Int,
    cellWidthPx: Int,
    spacingPx: Int,
    dividerWidthPx: Int,
    headerWidthPx: Int
): StickyForecastDate? {
    if (dates.isEmpty()) return null
    val starts = mutableListOf(0 to dates.first())
    var x = 0
    for (index in 1 until dates.size) {
        x += cellWidthPx + spacingPx
        if (dates[index] != dates[index - 1]) {
            x += dividerWidthPx + spacingPx
            starts += x to dates[index]
        }
    }
    // 다음 날짜의 글자가 고정 날짜 자리로 들어올 때 교체한다.
    val collisionThreshold = headerWidthPx
    val currentIndex = starts.indexOfLast { it.first - scrollPx <= collisionThreshold }.coerceAtLeast(0)
    return StickyForecastDate(starts[currentIndex].second)
}
