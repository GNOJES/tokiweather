package com.toki.weather.data.repository

import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.RefreshSource
import org.junit.Assert.*
import org.junit.Test

class ForegroundRefreshPolicyTest {
    private val now = 1_800_000_000_000L
    private fun cache(weatherAge: Long = 0, locationAge: Long = 0, saved: Boolean = false) =
        CachedWeather.EMPTY.copy(locationName = "목동", lastUpdated = now - weatherAge,
            refresh = CachedWeather.EMPTY.refresh.copy(locationConfirmedAt = now - locationAge, usedSavedLocation = saved))

    @Test fun waitsForCacheAndSettingsBeforeConsumingEntry() {
        val policy = ForegroundRefreshPolicy()
        assertNull(policy.takeRequest(1, null, now, 100_000, 30, false, true))
        assertNull(policy.takeRequest(1, cache(locationAge = 86_400_000), now, 100_000, null, false, true))
        assertEquals(RefreshSource.FOREGROUND, policy.takeRequest(1, cache(locationAge = 86_400_000), now, 100_000, 30, false, true))
    }

    @Test fun oldLocationTriggersEvenWhenWeatherWasRecentlyUpdated() {
        assertEquals(RefreshSource.FOREGROUND, ForegroundRefreshPolicy().takeRequest(
            1, cache(locationAge = 86_400_000), now, 100_000, 30, false, true))
    }

    @Test fun savedLocationTriggersEvenWithRecentConfirmation() {
        assertEquals(RefreshSource.FOREGROUND, ForegroundRefreshPolicy().takeRequest(
            1, cache(saved = true), now, 100_000, 30, false, true))
    }

    @Test fun oldWeatherAndMissingLocationAlsoTrigger() {
        assertEquals(RefreshSource.FOREGROUND, ForegroundRefreshPolicy().takeRequest(
            1, cache(weatherAge = 1_800_000), now, 100_000, 30, false, true))
        assertEquals(RefreshSource.FOREGROUND, ForegroundRefreshPolicy().takeRequest(
            1, cache().copy(refresh = CachedWeather.EMPTY.refresh), now, 100_000, 30, false, true))
    }

    @Test fun recentActualLocationAndWeatherDoNotTrigger() {
        assertNull(ForegroundRefreshPolicy().takeRequest(1, cache(locationAge = 900_000), now, 100_000, 30, false, true))
    }

    @Test fun doesNotRecheckBecauseCacheChangesWithinSameEntry() {
        val policy = ForegroundRefreshPolicy()
        assertNull(policy.takeRequest(1, cache(), now, 100_000, 30, false, true))
        assertNull(policy.takeRequest(1, cache(saved = true), now, 200_000, 30, false, true))
        assertEquals(RefreshSource.FOREGROUND, policy.takeRequest(2, cache(saved = true), now, 200_000, 30, false, true))
    }

    @Test fun limitsRapidReentryAndOverlappingRefresh() {
        val policy = ForegroundRefreshPolicy()
        val stale = cache(saved = true)
        assertEquals(RefreshSource.FOREGROUND, policy.takeRequest(1, stale, now, 100_000, 30, false, true))
        assertNull(policy.takeRequest(2, stale, now, 110_000, 30, false, true))
        assertNull(policy.takeRequest(3, stale, now, 170_000, 30, true, true))
        assertEquals(RefreshSource.FOREGROUND, policy.takeRequest(4, stale, now, 180_000, 30, false, true))
    }

    @Test fun existingCacheDoesNotPromptPermissionButEmptyCacheKeepsInitialFlow() {
        assertNull(ForegroundRefreshPolicy().takeRequest(1, cache(saved = true), now, 100_000, 30, false, false))
        assertEquals(RefreshSource.INITIAL, ForegroundRefreshPolicy().takeRequest(1, CachedWeather.EMPTY, now, 100_000, 30, false, false))
    }
}
