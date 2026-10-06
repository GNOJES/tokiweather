package com.toki.weather.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.toki.weather.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RefreshCommitTest {
    private suspend fun withStore(block: suspend (WeatherDataStore) -> Unit) {
        val directory = Files.createTempDirectory("toki-cache-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(WeatherDataStore(PreferenceDataStoreFactory.create(scope = scope,
                produceFile = { directory.resolve("test.preferences_pb") })))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); directory.deleteRecursively() }
    }
    @Test fun olderAddressDiagnosticCannotReplaceNewerSelection() = runBlocking {
        withStore { store ->
            val latest = com.toki.weather.util.AddressDiagnostics(200, com.toki.weather.util.AddressSelection("상암동"))
            store.saveAddressDiagnostics(latest)
            store.saveAddressDiagnostics(com.toki.weather.util.AddressDiagnostics(100, com.toki.weather.util.AddressSelection("영등포구")))
            assertEquals(latest, store.addressDiagnosticsFlow.first())
        }
    }
    @Test fun olderWeatherAndItsDelayedAirCannotOverwriteNewRegion() = runBlocking {
        withStore { store ->
            val old = store.beginRequest()
            val newer = store.beginRequest()
            assertTrue(store.commit(newer) { CachedWeather.EMPTY.copy(locationName = "상암동", currentTemp = 22) })
            assertFalse(store.commit(old) { CachedWeather.EMPTY.copy(locationName = "목동", currentTemp = 10) })
            assertFalse(store.commit(old, onlyIfCurrent = true) { it.copy(pm10 = 150) })
            assertEquals("상암동", store.weatherFlow.first().locationName)
            assertEquals(-1, store.weatherFlow.first().pm10)
        }
    }
    @Test fun overlappingRequestsRejectOldAirAfterNewWeatherIsVisible() = runBlocking {
        withStore { store ->
            val old = store.beginRequest()
            store.commit(old) { CachedWeather.EMPTY.copy(locationName = "목동") }
            val pendingAir = CompletableDeferred<Unit>()
            val releaseAir = CompletableDeferred<Unit>()
            val oldAir = async {
                pendingAir.complete(Unit)
                releaseAir.await()
                store.commit(old, onlyIfCurrent = true) { it.copy(pm10 = 150) }
            }
            pendingAir.await()
            val newer = store.beginRequest()
            store.commit(newer) { CachedWeather.EMPTY.copy(locationName = "상암동", currentTemp = 22) }
            releaseAir.complete(Unit)
            assertFalse(oldAir.await())
            assertEquals("상암동", store.weatherFlow.first().locationName)
            assertEquals(-1, store.weatherFlow.first().pm10)
        }
    }
    @Test fun airCompletionKeepsWeatherSuccessTimeAndForecast() = runBlocking {
        withStore { store ->
            val id = store.beginRequest()
            store.commit(id) { CachedWeather.EMPTY.copy(lastUpdated = 1234, currentTemp = 22) }
            store.commit(id, onlyIfCurrent = true) { it.copy(pm10 = 12, pmObservedAt = 1000) }
            assertEquals(1234, store.weatherFlow.first().lastUpdated)
            assertEquals(22, store.weatherFlow.first().currentTemp)
        }
    }
    @Test fun unsuccessfulNewAttemptDoesNotPreventOlderSuccessfulWeather() = runBlocking {
        withStore { store ->
            val old = store.beginRequest()
            store.beginRequest() // starts, then fails without saving
            assertTrue(store.commit(old) { CachedWeather.EMPTY.copy(currentTemp = 23) })
            assertEquals(23, store.weatherFlow.first().currentTemp)
        }
    }
    @Test fun diagnosticsAreBoundedAndSurviveStoreReopening() = runBlocking {
        val directory = Files.createTempDirectory("toki-events-test").toFile()
        val file = directory.resolve("events.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = WeatherDataStore(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            repeat(100) { first.record(RefreshEvent(it.toLong(), RefreshSource.AUTOMATIC,
                RefreshStage.LOCATION, RefreshState.FAILED, error = RefreshError.NETWORK)) }
            val address = com.toki.weather.util.AddressDiagnostics(100,
                com.toki.weather.util.LocationSelection.selectAddress(37.5, 126.9,
                    listOf(com.toki.weather.util.AddressCandidate(37.5032,126.9,subLocality="영등포동8가",locality="영등포구"))))
            first.saveAddressDiagnostics(address)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val reopened = WeatherDataStore(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))
            assertEquals(address, reopened.addressDiagnosticsFlow.first())
            val events = reopened.diagnosticsFlow.first()
            assertEquals(80, events.size)
            assertEquals(20L, events.first().requestId)
            assertEquals(99L, events.last().requestId)
            assertEquals(1L, reopened.beginRequest())
        } finally {
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            secondScope.coroutineContext[Job]!!.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
