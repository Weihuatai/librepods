package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class PhoneTransportScheduleTest {
    @Test fun holdAndReleaseTransitionsDoNotWaitForTheHeartbeatInterval() {
        assertEquals("HOLD", PhoneTransportSchedule.command(true, false, null, 0, 0, 0, false))
        assertEquals("ALLOW", PhoneTransportSchedule.command(false, true, true, 1, 0, 0, false))
    }
    @Test fun unchangedStateDoesNotSpawnDiagnosticsOnEveryAudit() {
        assertNull(PhoneTransportSchedule.command(true, true, true, 1_000, 0, 0, false))
        assertEquals("PING", PhoneTransportSchedule.command(true, true, true, 5_000, 0, 0, false))
        assertEquals("CHECK", PhoneTransportSchedule.command(true, true, true, 30_000, 25_000, 0, false))
    }
    @Test fun playbackEventReassertsTheHoldWithoutWaitingForTheSparseAudit() {
        assertEquals("REASSERT", PhoneTransportSchedule.command(true, true, true, 1_000, 0, 0, true))
    }
    @Test fun fullyReleasedOrDisabledStateDoesNotNeedARootProcess() {
        assertNull(PhoneTransportSchedule.command(false, false, null, 100_000, 0, 0, false))
    }
}
