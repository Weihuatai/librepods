package me.kavishdevar.librepods.services

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSettingsAncCycleTest {
    @Test fun repeatedClicksWrapFromNoiseCancellationToTransparencyWithoutOff() {
        // AirPods wire values: Transparency=3, Adaptive=4, ANC=2, Off=1.
        var reportedMode = 3
        val requestedModes = List(9) {
            QuickSettingsAncCycle.next(reportedMode).also { reportedMode = it }
        }
        assertEquals(listOf(4, 2, 3, 4, 2, 3, 4, 2, 3), requestedModes)
    }

    @Test fun offSelectedElsewhereEntersTheThreeModeCycleAtTransparency() {
        assertEquals(3, QuickSettingsAncCycle.next(1))
    }

    @Test fun unrecognizedReportEntersTheCycleWithoutRequestingOff() {
        assertEquals(3, QuickSettingsAncCycle.next(0))
        assertEquals(3, QuickSettingsAncCycle.next(255))
    }
}
