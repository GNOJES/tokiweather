package com.toki.weather.worker

class WidgetWorkCoordinator(
    private val schedule: (Long) -> Unit,
    private val cancel: () -> Unit,
    private val activeWidgetCounts: () -> Pair<Int, Int>,
    private val savedIntervalMinutes: suspend () -> Long,
    private val hasActiveSchedule: suspend () -> Boolean = { false }
) {
    suspend fun onWidgetEnabled() { schedule(savedIntervalMinutes()) }
    suspend fun onWidgetUpdated() {
        val (standard, large) = activeWidgetCounts()
        if (standard + large > 0 && !hasActiveSchedule()) schedule(savedIntervalMinutes())
    }
    fun onWidgetDisabled() {
        val (standard, large) = activeWidgetCounts()
        if (standard == 0 && large == 0) cancel()
    }
}
