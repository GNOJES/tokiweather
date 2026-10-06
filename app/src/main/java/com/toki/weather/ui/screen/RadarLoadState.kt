package com.toki.weather.ui.screen

/** A failed main frame stays failed even when WebView emits onPageFinished afterwards. */
data class RadarLoadState(val loading: Boolean = true, val error: Boolean = false) {
    fun failed(mainFrame: Boolean) = if (mainFrame) RadarLoadState(false, true) else this
    fun finished() = copy(loading = false)
}
