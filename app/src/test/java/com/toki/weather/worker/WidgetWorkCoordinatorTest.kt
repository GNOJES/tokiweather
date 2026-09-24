package com.toki.weather.worker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WidgetWorkCoordinatorTest {
    @Test fun removingOneWidgetTypeKeepsSharedSchedule() {
        var cancellations = 0
        val coordinator = WidgetWorkCoordinator(
            schedule = {}, cancel = { cancellations++ },
            activeWidgetCounts = { 0 to 1 }, savedIntervalMinutes = { 60L }
        )
        coordinator.onWidgetDisabled()
        assertEquals(0, cancellations)
    }

    @Test fun removingLastWidgetCancelsSharedSchedule() {
        var cancellations = 0
        val coordinator = WidgetWorkCoordinator(
            schedule = {}, cancel = { cancellations++ },
            activeWidgetCounts = { 0 to 0 }, savedIntervalMinutes = { 60L }
        )
        coordinator.onWidgetDisabled()
        assertEquals(1, cancellations)
    }

    @Test fun addingOtherTypeUsesSavedInterval() = runBlocking {
        var scheduledInterval = -1L
        val coordinator = WidgetWorkCoordinator(
            schedule = { scheduledInterval = it }, cancel = {},
            activeWidgetCounts = { 1 to 1 }, savedIntervalMinutes = { 60L }
        )
        coordinator.onWidgetEnabled()
        assertEquals(60L, scheduledInterval)
    }

    @Test fun existingWidgetRestoresMissingPeriodicWork() = runBlocking {
        var scheduledInterval = -1L
        val coordinator = WidgetWorkCoordinator(
            schedule = { scheduledInterval = it }, cancel = {},
            activeWidgetCounts = { 0 to 1 }, savedIntervalMinutes = { 15L },
            hasActiveSchedule = { false }
        )
        coordinator.onWidgetUpdated()
        assertEquals(15L, scheduledInterval)
    }

    @Test fun widgetUpdateDoesNotResetExistingSchedule() = runBlocking {
        var schedules = 0
        val coordinator = WidgetWorkCoordinator(
            schedule = { schedules++ }, cancel = {},
            activeWidgetCounts = { 1 to 0 }, savedIntervalMinutes = { 15L },
            hasActiveSchedule = { true }
        )
        coordinator.onWidgetUpdated()
        assertEquals(0, schedules)
    }
}
