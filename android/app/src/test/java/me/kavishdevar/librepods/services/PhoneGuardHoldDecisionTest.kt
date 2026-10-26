package me.kavishdevar.librepods.services

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneGuardHoldDecisionTest {
    private val hold = PhoneGuardHoldDecision.Action.HOLD
    private val wait = PhoneGuardHoldDecision.Action.WAIT
    private val restore = PhoneGuardHoldDecision.Action.RESTORE
    @Test fun unknownMacStateWithReadyAirpodsIsProtectedEvenBeforePeerReply() {
        assertEquals(hold, PhoneGuardHoldDecision.decide(true, true, false, false, false, true))
    }
    @Test fun mediaRouteChangeAfterHoldCannotCauseReleaseAndReholdLoop() {
        assertEquals(hold, PhoneGuardHoldDecision.decide(true, true, true, false, true, false))
    }
    @Test fun temporaryControlLossDoesNotReleaseAConnectedOwnedHold() {
        assertEquals(hold, PhoneGuardHoldDecision.decide(true, true, false, false, true, false))
    }
    @Test fun unconfirmedMediaWaitsWithoutIssuingAnAllow() {
        assertEquals(wait, PhoneGuardHoldDecision.decide(true, null, false, false, false, false))
    }
    @Test fun explicitMacStopReleasesImmediatelyEvenWithAStoredLease() {
        assertEquals(restore, PhoneGuardHoldDecision.decide(true, true, true, true, true, true))
    }
    @Test fun disabledModeOrActualAclDisconnectRestoresTheSavedState() {
        assertEquals(restore, PhoneGuardHoldDecision.decide(false, true, true, false, true, true))
        assertEquals(restore, PhoneGuardHoldDecision.decide(true, false, true, false, true, true))
    }
}
