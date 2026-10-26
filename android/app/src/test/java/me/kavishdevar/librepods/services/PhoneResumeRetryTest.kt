package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class PhoneResumeRetryTest {
    @Test fun failedSendDoesNotMarkRecoveryCompleteAndCanRetry() {
        val retry = PhoneResumeRetry()
        val first = retry.begin(0)!!
        assertTrue(retry.finish(first, 0, false))
        assertNull(retry.begin(249))
        val second = retry.begin(250)!!
        assertTrue(retry.finish(second, 250, true))
        assertNull(retry.begin(50_000))
    }
    @Test fun aResetForNewRemotePlaybackRejectsAnOldSuccess() {
        val retry = PhoneResumeRetry()
        val old = retry.begin(0)!!
        retry.reset()
        val current = retry.begin(1)!!
        assertFalse(retry.finish(old, 2, true))
        assertTrue(retry.finish(current, 2, false))
    }
    @Test fun onlyOneAttemptIsInFlightAndBackoffIsBounded() {
        val retry = PhoneResumeRetry()
        var now = 0L
        repeat(15) {
            val token = retry.begin(now)!!
            assertNull(retry.begin(now))
            assertTrue(retry.finish(token, now, false))
            assertTrue(retry.retryAt - now in 250L..5_000L)
            now = retry.retryAt
        }
    }
}
