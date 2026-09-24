package com.toki.weather.util

import org.junit.Assert.*
import org.junit.Test

class LocationSelectionTest {
    private val lat = 37.5238748
    private val lon = 126.9052288
    @Test fun rejectsActualDistantYeongdeungpo8GaResult() {
        val address = AddressCandidate(37.5265892, 126.9032158, "영등포동8가", subLocality = "영등포구")
        assertEquals("영등포구", LocationSelection.addressName(lat, lon, listOf(address)))
    }
    @Test fun choosesNearbyCandidateInsteadOfFirstDongName() {
        val far = AddressCandidate(37.5265892, 126.9032158, "영등포동8가", subLocality = "영등포구")
        val near = AddressCandidate(lat, lon, "영등포동7가", subLocality = "영등포구")
        assertEquals("영등포동7가", LocationSelection.addressName(lat, lon, listOf(far, near)))
    }
    @Test fun structuredDongIsPreferredOverBuildingName() {
        val near = AddressCandidate(lat, lon, "국회대로", "101동", "대한민국 서울특별시 영등포구 영등포동7가 1", "영등포구")
        assertEquals("영등포동7가", LocationSelection.addressName(lat, lon, listOf(near)))
    }
    @Test fun unlocatedDongDoesNotMasqueradeAsLocal() {
        assertEquals("영등포구", LocationSelection.addressName(lat, lon, listOf(AddressCandidate(null, null, "영등포동8가", subLocality = "영등포구"))))
    }
    @Test fun freshFixWinsOverAccurateButOldGps() {
        assertEquals(1, LocationSelection.fixIndex(listOf(LocationFix(1_000_000_000L, 3f), LocationFix(60_000_000_000L, 20f)), 61_000_000_000L))
    }
    @Test fun staleAndFutureFixesAreRejected() {
        assertNull(LocationSelection.fixIndex(listOf(LocationFix(1L, 3f), LocationFix(301_000_000_000L, 2f)), 300_000_000_000L))
    }
    @Test fun comparableFreshFixesUseAccuracy() {
        assertEquals(0, LocationSelection.fixIndex(listOf(LocationFix(59_000_000_000L, 3f), LocationFix(60_000_000_000L, 20f)), 61_000_000_000L))
    }
}
