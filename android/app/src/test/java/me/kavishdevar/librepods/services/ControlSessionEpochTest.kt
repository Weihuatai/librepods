package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ControlSessionEpochTest {
    @Test(timeout = 5_000) fun readingAndInvalidatingNeverWaitForAnInFlightProtocolAction() {
        val sessions = ControlSessionEpoch()
        val token = sessions.begin()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val worker = Thread {
            sessions.runIfCurrent(token) {
                entered.countDown()
                release.await(3, TimeUnit.SECONDS)
                assertFalse(sessions.isCurrent(token))
            }
            done.countDown()
        }.apply { isDaemon = true; start() }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertEquals(token, sessions.snapshot().generation)
            sessions.invalidate()
            assertFalse(sessions.isCurrent(token))
        } finally { release.countDown() }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        worker.join(100)
    }
    @Test(timeout = 5_000) fun serviceMonitorAndSessionSnapshotCannotFormThePreviousDeadlock() {
        val sessions = ControlSessionEpoch()
        val token = sessions.begin()
        val service = Any()
        val readerEntered = CountDownLatch(1)
        val mainEntered = CountDownLatch(1)
        val done = CountDownLatch(2)
        Thread {
            sessions.runIfCurrent(token) {
                readerEntered.countDown()
                mainEntered.await(2, TimeUnit.SECONDS)
                synchronized(service) { sessions.snapshot() }
            }
            done.countDown()
        }.apply { isDaemon = true; start() }
        assertTrue(readerEntered.await(2, TimeUnit.SECONDS))
        Thread {
            synchronized(service) {
                mainEntered.countDown()
                sessions.snapshot()
            }
            done.countDown()
        }.apply { isDaemon = true; start() }
        assertTrue(done.await(2, TimeUnit.SECONDS))
    }
    @Test fun socketConnectionAloneDoesNotConfirmAnEarbudReply() {
        val sessions = ControlSessionEpoch()
        val token = sessions.begin()
        assertTrue(sessions.isCurrent(token))
        assertEquals(0, sessions.snapshot().replies)
    }
    @Test fun reconnectRejectsOldInitializationWork() {
        val sessions = ControlSessionEpoch()
        val old = sessions.begin()
        val current = sessions.begin()
        var called = false
        assertFalse(sessions.runIfCurrent(old) { called = true })
        assertFalse(called)
        assertTrue(sessions.isCurrent(current))
    }
    @Test fun oldReaderCannotDisconnectTheNewConnection() {
        val sessions = ControlSessionEpoch()
        val old = sessions.begin()
        val current = sessions.begin()
        assertFalse(sessions.finish(old))
        assertTrue(sessions.isCurrent(current))
    }
    @Test fun queuedOldDisconnectIsRejectedAfterReconnect() {
        val sessions = ControlSessionEpoch()
        val old = sessions.begin()
        assertTrue(sessions.finish(old))
        sessions.begin()
        assertFalse(sessions.isLatest(old))
    }
    @Test fun manualDisconnectInvalidatesPendingRetriesAndReplies() {
        val sessions = ControlSessionEpoch()
        val old = sessions.begin()
        val disconnected = sessions.invalidate()
        assertFalse(sessions.isCurrent(old))
        assertFalse(sessions.reply(old))
        assertTrue(sessions.isLatest(disconnected))
    }
    @Test fun oldReplyCannotMakeNewConnectionAppearReady() {
        val sessions = ControlSessionEpoch()
        val old = sessions.begin()
        val current = sessions.begin()
        assertFalse(sessions.reply(old))
        assertEquals(0, sessions.snapshot().replies)
        assertTrue(sessions.reply(current))
        assertEquals(1, sessions.snapshot().replies)
    }
    @Test fun finishDisablesFurtherWorkEvenBeforeDisconnectBroadcast() {
        val sessions = ControlSessionEpoch()
        val token = sessions.begin()
        assertTrue(sessions.finish(token))
        assertFalse(sessions.runIfCurrent(token) { fail("Finished session must not send") })
        assertTrue(sessions.isLatest(token))
    }
    @Test fun newConnectionStartsWithNoRepliesFromPreviousConnection() {
        val sessions = ControlSessionEpoch()
        sessions.reply(sessions.begin())
        sessions.begin()
        assertEquals(0, sessions.snapshot().replies)
    }
}
