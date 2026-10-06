package com.toki.weather.data.repository

import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException

/** Persistent fixed-category stage timing. Raw exceptions must never be serialized. */
class RefreshTrace(private val store: WeatherDataStore, val id: Long, val source: RefreshSource) {
    suspend fun event(stage: RefreshStage, state: RefreshState, duration: Long = 0,
                      error: RefreshError? = null) {
        store.record(RefreshEvent(id, source, stage, state, durationMs = duration, error = error))
    }
    suspend fun <T> stage(stage: RefreshStage, block: suspend () -> T): T {
        event(stage, RefreshState.STARTED)
        val start = System.nanoTime()
        try {
            return block().also { event(stage, RefreshState.SUCCESS, (System.nanoTime() - start) / 1_000_000) }
        } catch (e: Exception) {
            val state = when (e) {
                is TimeoutCancellationException -> RefreshState.TIMEOUT
                is CancellationException -> RefreshState.CANCELLED
                else -> RefreshState.FAILED
            }
            val error = when (e) {
                is IOException -> RefreshError.NETWORK
                is SecurityException -> RefreshError.PERMISSION
                is IllegalStateException, is IllegalArgumentException -> RefreshError.INVALID_DATA
                else -> RefreshError.OTHER
            }
            // Cancellation cannot erase the record of the interrupted stage.
            withContext(NonCancellable) { event(stage, state, (System.nanoTime() - start) / 1_000_000, error) }
            throw e
        }
    }
}
