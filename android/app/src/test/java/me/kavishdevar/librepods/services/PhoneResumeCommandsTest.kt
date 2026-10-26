package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class PhoneResumeCommandsTest {
    @Test fun failureAtAnySendCanRetryAndOnlyACompleteAttemptFinishesRecovery() {
        for (failureAt in 1..3) {
            val retry = PhoneResumeRetry()
            val first = retry.begin(0)!!
            var sends = 0
            val failing = { ++sends != failureAt }
            val failed = PhoneResumeCommands.send({ true }, failing, failing, failing)
            assertFalse(failed.success)
            assertEquals(failureAt, sends)
            assertTrue(retry.finish(first, 0, failed.success))
            val second = retry.begin(retry.retryAt)!!
            val success = PhoneResumeCommands.send({ true }, { true }, { true }, { true })
            assertTrue(retry.finish(second, retry.retryAt, success.success))
            assertNull(retry.begin(100_000))
        }
    }
    @Test fun aRemoteRestartBetweenCommandsStopsTheRemainingClaim() {
        var allowed = true
        var laterSends = 0
        val result = PhoneResumeCommands.send({ allowed }, { allowed = false; true },
            { laterSends++; true }, { laterSends++; true })
        assertFalse(result.success)
        assertEquals(0, laterSends)
    }
    @Test fun noOwnershipPacketIsSentWhenProtectionHasResumed() {
        var sends = 0
        val send = { sends++; true }
        assertFalse(PhoneResumeCommands.send({ false }, send, send, send).success)
        assertEquals(0, sends)
    }
}
