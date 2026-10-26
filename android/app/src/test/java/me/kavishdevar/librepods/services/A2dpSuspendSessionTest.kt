package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class A2dpSuspendSessionTest {
    private class Audio : A2dpSuspendSession.Access {
        var state = A2dpSuspendSnapshot(false, false, false)
        var reads = 0
        val writes = mutableListOf<Boolean>()
        override fun set(suspended: Boolean) { writes.add(suspended); state = state.copy(external = suspended, applied = suspended) }
        override fun snapshot(): A2dpSuspendSnapshot { reads++; return state }
    }
    @Test fun heartbeatsNeitherDumpAudioNorRewriteTheGlobalSuspendBit() {
        val audio = Audio()
        val session = A2dpSuspendSession(false, audio)
        session.command("HOLD")
        repeat(100) { assertEquals("PONG", session.command("PING")) }
        assertEquals(1, audio.reads)
        assertEquals(listOf(true), audio.writes)
    }
    @Test fun reassertOnPlaybackIsImmediateWithoutAFullDiagnosticDump() {
        val audio = Audio()
        val session = A2dpSuspendSession(false, audio)
        assertEquals("HELD", session.command("REASSERT"))
        assertEquals(listOf(true), audio.writes)
        assertEquals(0, audio.reads)
    }
    @Test fun sparseAuditRepairsSuspendClearedByTheSystem() {
        val audio = Audio()
        val session = A2dpSuspendSession(false, audio)
        session.command("CHECK")
        assertTrue(audio.state.external)
        assertEquals(2, audio.reads)
        assertEquals(listOf(true), audio.writes)
    }
    @Test fun normalAuditReadsOnceWithoutReapplyingAnUnchangedState() {
        val audio = Audio()
        val session = A2dpSuspendSession(false, audio)
        session.command("HOLD"); session.command("CHECK")
        assertEquals(2, audio.reads)
        assertEquals(listOf(true), audio.writes)
    }
    @Test fun releaseAndCloseRestoreTheOriginalStateIncludingAnOriginallySuspendedSystem() {
        for (original in listOf(false, true)) {
            val audio = Audio()
            val session = A2dpSuspendSession(original, audio)
            session.command("HOLD"); session.command("ALLOW")
            assertEquals(original, audio.state.external)
            session.close()
            val writes = audio.writes.size
            session.close()
            assertEquals(writes, audio.writes.size)
            assertEquals(original, audio.state.external)
        }
    }
    @Test fun steadyHourUsesSparseDiagnosticsInsteadOfOneDumpPerSecond() {
        val audio = Audio()
        val session = A2dpSuspendSession(false, audio)
        var lastCommand = 0L
        var verified = 0L
        session.command("HOLD")
        for (now in 1_000L..3_600_000L step 1_000L) {
            val command = PhoneTransportSchedule.command(true, true, true, now, lastCommand, verified, false)
            if (command != null) {
                session.command(command); lastCommand = now
                if (command == "CHECK") verified = now
            }
        }
        assertEquals(121, audio.reads) // Initial HOLD plus 120 half-minute audits.
    }
}
