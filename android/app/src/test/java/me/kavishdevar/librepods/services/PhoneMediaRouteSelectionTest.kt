package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class PhoneMediaRouteSelectionTest {
    private val target = "AA:BB:CC:DD:EE:01"
    @Test fun targetAirpodsRemainEligibleForProtection() {
        assertEquals(PhoneMediaRouteSelection.Selection(true, false), PhoneMediaRouteSelection.classify(
            listOf(PhoneMediaRouteSelection.Output(true, true, target.lowercase())), target))
    }
    @Test fun anotherA2dpDestinationIsExplicitOptOutEvenWithAirpodsStillConnected() {
        assertEquals(PhoneMediaRouteSelection.Selection(false, true), PhoneMediaRouteSelection.classify(
            listOf(PhoneMediaRouteSelection.Output(true, true, "AA:BB:CC:DD:EE:02")), target))
    }
    @Test fun emptyAddressUnknownRouteAndSpeakerFallbackCannotReleaseTheLease() {
        val variants = listOf(null, emptyList(), listOf(PhoneMediaRouteSelection.Output(false, false, "")),
            listOf(PhoneMediaRouteSelection.Output(true, true, "")))
        for (outputs in variants) assertEquals(PhoneMediaRouteSelection.Selection(false, false),
            PhoneMediaRouteSelection.classify(outputs, target))
    }
    @Test fun theTargetsOwnCallProfileIsNotMistakenForAnUnrelatedHeadset() {
        val route = PhoneMediaRouteSelection.classify(listOf(PhoneMediaRouteSelection.Output(true, false, target)), target)
        assertFalse(route.otherBluetooth)
    }
}
