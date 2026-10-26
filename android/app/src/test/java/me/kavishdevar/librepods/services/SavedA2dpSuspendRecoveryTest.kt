package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class SavedA2dpSuspendRecoveryTest {
    private class Store(var saved: Boolean?) : SavedA2dpSuspendRecovery.Store {
        override fun original() = saved
        override fun clear() { saved = null }
    }
    @Test fun absentRecordNeverCallsSystemAudio() {
        val store = Store(null)
        val recovery = SavedA2dpSuspendRecovery(store) { error("Must not change audio without a baseline") }
        assertNull(recovery.recover())
    }
    @Test fun confirmedRestoreClearsOnlyAfterAcknowledgement() {
        val store = Store(false)
        val recovery = SavedA2dpSuspendRecovery(store) {
            assertEquals(false, store.saved)
            assertFalse(it)
            A2dpSuspendSnapshot(false, false, false)
        }
        assertEquals(A2dpSuspendSnapshot(false, false, false), recovery.recover())
        assertNull(store.saved)
    }
    @Test fun failureKeepsBaselineForManualRetry() {
        val store = Store(false)
        val recovery = SavedA2dpSuspendRecovery(store) { error("Root denied") }
        assertThrows(IllegalStateException::class.java) { recovery.recover() }
        assertEquals(false, store.saved)
    }
    @Test fun mismatchedSystemStateCannotEraseRecord() {
        val store = Store(false)
        val recovery = SavedA2dpSuspendRecovery(store) { A2dpSuspendSnapshot(true, false, true) }
        assertThrows(IllegalStateException::class.java) { recovery.recover() }
        assertEquals(false, store.saved)
    }
    @Test fun originalTrueIsNotForcedToFalse() {
        val store = Store(true)
        val recovery = SavedA2dpSuspendRecovery(store) {
            assertTrue(it)
            A2dpSuspendSnapshot(true, false, true)
        }
        assertEquals(true, recovery.recover()?.external)
        assertNull(store.saved)
    }
    @Test fun internalStackSuspendDoesNotPreventRestoringOurExternalBit() {
        val store = Store(false)
        val recovery = SavedA2dpSuspendRecovery(store) { A2dpSuspendSnapshot(false, true, true) }
        assertEquals(A2dpSuspendSnapshot(false, true, true), recovery.recover())
        assertNull(store.saved)
    }
}
