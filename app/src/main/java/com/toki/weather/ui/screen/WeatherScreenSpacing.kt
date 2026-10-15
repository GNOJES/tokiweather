package com.toki.weather.ui.screen

/** Extra top/bottom padding for each of the five cards, using measured natural height. */
internal fun weatherCardExtraPadding(
    enabled: Boolean,
    availableHeight: Float,
    naturalHeight: Float?
): Float {
    if (!enabled || naturalHeight == null || !availableHeight.isFinite() ||
        !naturalHeight.isFinite()) return 0f
    return ((availableHeight - naturalHeight).coerceAtLeast(0f) / 10f)
}
