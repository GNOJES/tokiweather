package com.toki.weather.ui.screen
import org.junit.Test
import org.junit.Assert.*
class RadarLoadStateTest {
    @Test fun pageFinishedDoesNotHideMainFrameFailure() {
        assertTrue(RadarLoadState().failed(true).finished().error)
    }
    @Test fun subresourceFailureDoesNotReplaceMainPage() {
        assertEquals(RadarLoadState(), RadarLoadState().failed(false))
        assertFalse(RadarLoadState().finished().failed(false).error)
    }
    @Test fun retryStartsCleanLoadingState() {
        val failed = RadarLoadState().failed(true)
        assertFalse(failed.loading)
        assertFalse(RadarLoadState().error)
        assertTrue(RadarLoadState().loading)
    }
}
