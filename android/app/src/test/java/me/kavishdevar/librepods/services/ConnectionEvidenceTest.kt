package me.kavishdevar.librepods.services
import org.junit.Assert.*
import org.junit.Test
class ConnectionEvidenceTest {
    @Test fun savedDeviceWithoutCurrentEvidenceCannotConnect() {
        assertFalse(ConnectionEvidence.mayAttemptAutomatically(false, false, null, false, false))
    }
    @Test fun socketFlagAloneCannotShowConnected() {
        assertFalse(ConnectionEvidence.mayShowConnected(false, true, null, false))
    }
    @Test fun oldReplyWithoutLiveAclCannotShowConnected() {
        assertFalse(ConnectionEvidence.mayShowConnected(true, false, null, false))
    }
    @Test fun freshlyDockedEarbudsCannotTriggerTakeoverOrPopup() {
        assertFalse(ConnectionEvidence.mayAttemptAutomatically(true, true, 100, false, true))
        assertFalse(ConnectionEvidence.mayShowConnected(true, true, 100, true))
    }
    @Test fun staleWearingStateCannotTriggerTakeover() {
        assertFalse(ConnectionEvidence.mayAttemptAutomatically(false, false, 5_001, true, false))
    }
    @Test fun clockRollbackDoesNotMakeOldWearingEvidenceFresh() {
        assertFalse(ConnectionEvidence.mayAttemptAutomatically(false, false, -1, true, false))
    }
    @Test fun freshWearingEvidenceCanInitiateButCannotClaimSuccess() {
        assertTrue(ConnectionEvidence.mayAttemptAutomatically(false, false, 100, true, false))
        assertFalse(ConnectionEvidence.mayShowConnected(false, false, 100, false))
    }
    @Test fun confirmedConnectionCanShowSuccessWithoutBleBroadcasts() {
        assertTrue(ConnectionEvidence.mayShowConnected(true, true, null, false))
    }
}
