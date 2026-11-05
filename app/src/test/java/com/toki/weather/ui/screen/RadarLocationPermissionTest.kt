package com.toki.weather.ui.screen

import org.junit.Assert.*
import org.junit.Test

class RadarLocationPermissionTest {
    private val origin = "https://weather.naver.com/"
    private val page = "https://weather.naver.com/map?visualMapType=maple"

    @Test fun existingAppPermissionAllowsWeatherOriginImmediately() {
        val gate = RadarLocationPermission()
        val results = mutableListOf<Boolean>()
        assertFalse(gate.request(origin, page, true, results::add))
        assertEquals(listOf(true), results)
    }

    @Test fun missingAppPermissionWaitsForRuntimeDecision() {
        for (granted in listOf(true, false)) {
            val gate = RadarLocationPermission()
            val results = mutableListOf<Boolean>()
            assertTrue(gate.request(origin, page, false, results::add))
            assertTrue(results.isEmpty())
            assertTrue(gate.complete(granted))
            assertEquals(listOf(granted), results)
            assertFalse(gate.complete(!granted))
        }
    }

    @Test fun unrelatedOrInsecureOriginsNeverRequestAndroidPermission() {
        val gate = RadarLocationPermission()
        listOf("http://weather.naver.com", "https://weather.naver.com.evil.test",
            "https://naver.com", "https://evil.test", "https://weather.naver.com:444",
            "https://user@weather.naver.com", "invalid").forEach { untrusted ->
            val results = mutableListOf<Boolean>()
            assertFalse(gate.request(untrusted, page, true, results::add))
            assertEquals(listOf(false), results)
        }
    }

    @Test fun weatherFrameOnUntrustedTopPageIsDenied() {
        val gate = RadarLocationPermission()
        listOf(null, "http://weather.naver.com/map", "https://evil.test/map").forEach { top ->
            val results = mutableListOf<Boolean>()
            assertFalse(gate.request(origin, top, true, results::add))
            assertEquals(listOf(false), results)
        }
    }

    @Test fun navigationOrDisposalCancelsPendingRequestExactlyOnce() {
        val gate = RadarLocationPermission()
        val results = mutableListOf<Boolean>()
        gate.request(origin, page, false, results::add)
        gate.cancel()
        gate.cancel()
        assertFalse(gate.complete(true))
        assertEquals(listOf(false), results)
    }

    @Test fun newRequestDeniesOldRequestAndOnlyNewOneReceivesPermissionResult() {
        val gate = RadarLocationPermission()
        val old = mutableListOf<Boolean>()
        val current = mutableListOf<Boolean>()
        gate.request(origin, page, false, old::add)
        gate.request(origin, page, false, current::add)
        assertEquals(listOf(false), old)
        assertTrue(current.isEmpty())
        gate.complete(true)
        assertEquals(listOf(true), current)
        assertEquals(listOf(false), old)
    }
}
