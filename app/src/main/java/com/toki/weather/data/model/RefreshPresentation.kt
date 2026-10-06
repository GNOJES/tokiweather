package com.toki.weather.data.model

fun CachedWeather.usesPreviousWeather(now: Long, intervalMinutes: Int, events: List<RefreshEvent>): Boolean =
    lastUpdated > 0 && (now - lastUpdated > intervalMinutes * 60_000L ||
        events.any { it.source != RefreshSource.DISPLAY && it.stage == RefreshStage.REQUEST &&
            it.requestId > refresh.requestId && it.at > lastUpdated &&
            it.state in setOf(RefreshState.FAILED, RefreshState.TIMEOUT, RefreshState.CANCELLED) })
