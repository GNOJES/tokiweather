package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.RefreshSource

/** Check once per foreground entry after cache/settings load; never loop on cache emissions. */
class ForegroundRefreshPolicy {
    private var checkedEntry: Int? = null
    private var lastAttemptElapsed: Long? = null

    fun takeRequest(
        entry: Int, weather: CachedWeather?, now: Long, elapsed: Long,
        intervalMinutes: Int?, isRefreshing: Boolean, hasLocationPermission: Boolean
    ): RefreshSource? {
        if (weather == null || intervalMinutes == null || checkedEntry == entry) return null
        checkedEntry = entry
        if (isRefreshing) return null
        val empty = weather.lastUpdated <= 0 || weather.locationName == "설정 위치"
        val maxAge = intervalMinutes.coerceAtLeast(1) * 60_000L
        val locationAt = weather.refresh.locationConfirmedAt
        val needsRefresh = empty || weather.refresh.usedSavedLocation ||
            now - weather.lastUpdated !in 0 until maxAge ||
            locationAt == null || locationAt <= 0 || now - locationAt !in 0 until maxAge
        if (!needsRefresh || (!empty && !hasLocationPermission)) return null
        // A permission dialog or brief app switch must not immediately repeat a failed request.
        if (lastAttemptElapsed?.let { elapsed - it in 0 until 60_000L } == true) return null
        lastAttemptElapsed = elapsed
        return if (empty) RefreshSource.INITIAL else RefreshSource.FOREGROUND
    }
}
