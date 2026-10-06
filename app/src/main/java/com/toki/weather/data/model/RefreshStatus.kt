package com.toki.weather.data.model

/** Only fixed categories and numbers are persisted. No URL, exception message or key. */
enum class RefreshSource { MANUAL, INITIAL, AUTOMATIC, SETTINGS, DISPLAY }
enum class RefreshStage { REQUEST, LOCATION, OBSERVATION, SHORT_FORECAST, PREVIOUS_FORECAST, ULTRA_FORECAST, WEATHER_SAVE, AIR_STATIONS, AIR_MEASUREMENT, AIR_QUALITY, AIR_SAVE, WIDGET }
enum class RefreshState { STARTED, SUCCESS, FAILED, TIMEOUT, CANCELLED, SUPERSEDED, REQUESTED, MISSING, RETAINED }
enum class RefreshError { NETWORK, PERMISSION, INVALID_DATA, OTHER }
data class RefreshEvent(
    val requestId: Long,
    val source: RefreshSource,
    val stage: RefreshStage,
    val state: RefreshState,
    val at: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val error: RefreshError? = null
)
data class RefreshMetadata(
    val requestId: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationConfirmedAt: Long? = null,
    val weatherObservedAt: Long? = null,
    val usedSavedLocation: Boolean = false,
    val airQualityRetained: Boolean = false,
    val airQualityPending: Boolean = false
)
