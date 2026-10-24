package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class ControlSessionEpochTest {
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
