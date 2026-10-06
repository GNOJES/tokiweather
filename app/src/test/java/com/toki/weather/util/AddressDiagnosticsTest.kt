package com.toki.weather.util
import org.junit.Test
import org.junit.Assert.*
import com.google.gson.Gson
class AddressDiagnosticsTest {
    @Test fun distantDongFallsBackToDistrictWithReason() {
        val result = LocationSelection.selectAddress(37.5,126.9,listOf(
            AddressCandidate(37.5032,126.9,subLocality="영등포동8가",locality="영등포구")))
        assertEquals("영등포구",result.name)
        assertEquals(AddressReason.OUTSIDE_200M,result.candidates.single().reason)
        assertTrue(result.candidates.single().distanceMeters!! > 200)
    }
    @Test fun closestValidDongWinsAndDoesNotPersistFullAddress() {
        val result = LocationSelection.selectAddress(37.5,126.9,listOf(
            AddressCandidate(37.501,126.9,addressLine="서울 영등포동8가 비밀건물 123", locality="영등포구"),
            AddressCandidate(37.5001,126.9,subLocality="영등포동7가")))
        assertEquals("영등포동7가",result.name)
        assertEquals(AddressReason.SELECTED,result.candidates[1].reason)
        assertFalse(Gson().toJson(result).contains("비밀건물"))
    }
    @Test fun invalidCoordinatesAreExplainedAndListIsBounded() {
        val result=LocationSelection.selectAddress(37.5,126.9,List(8){AddressCandidate(null,null,locality="영등포구")})
        assertEquals(5,result.candidates.size)
        assertEquals(AddressReason.INVALID_COORDINATES,result.candidates.first().reason)
        assertNull(result.candidates.first().distanceMeters)
    }
}
