package com.toki.weather.worker

import com.toki.weather.data.model.CachedWeather
import kotlinx.coroutines.CancellationException

data class SettingsRefreshOutcome(val weatherFailure: Throwable?, val displayFailure: Throwable?)

/** Saving settings must not depend on a weather request succeeding. */
suspend fun saveSettingsAndRefresh(
    save: suspend () -> Unit,
    fetch: suspend () -> Result<CachedWeather>,
    render: suspend () -> Unit
): SettingsRefreshOutcome {
    save()
    suspend fun display(): Throwable? = try { render(); null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { e }
    var displayFailure = display()
    val weatherFailure = try { fetch().exceptionOrNull()?.also { if (it is CancellationException) throw it } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { e }
    if (weatherFailure == null) displayFailure = display()
    return SettingsRefreshOutcome(weatherFailure, displayFailure)
}
