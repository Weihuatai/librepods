package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class AudioProfileRouteControllerTest {
    private class Fake(var policy: Int = 100, var state: Int = 2) : AudioProfileRouteController.Access {
        val writes = mutableListOf<Int>()
        var accepts = true
        var applies = true
        var disconnects = true
        override fun connectionPolicy() = policy
        override fun connectionState() = state
        override fun setConnectionPolicy(policy: Int): Boolean {
            writes.add(policy)
            if (accepts && applies) {
                this.policy = policy
                if (policy == 0 && disconnects) state = 0
            }
            return accepts
        }
    }
    @Test fun holdSavesOriginalBeforeDisablingTheRoute() {
        val fake = Fake()
        var saved = -1
        val result = AudioProfileRouteController.hold(fake) {
            assertTrue(fake.writes.isEmpty())
            saved = it
        }
        assertEquals(100, saved)
        assertTrue(result.held)
    }
    @Test fun reopenedRouteIsClosedOnTheNextAudit() {
        val fake = Fake()
        AudioProfileRouteController.hold(fake) {}
        fake.policy = 100; fake.state = 2
        assertTrue(AudioProfileRouteController.hold(fake) {}.held)
        assertEquals(listOf(0, 0), fake.writes)
    }
    @Test fun forbiddenPolicyWithConnectedAudioIsNotTreatedAsProtected() {
        val fake = Fake(0, 2)
        assertTrue(AudioProfileRouteController.hold(fake) { fail("Must not save a blocked policy") }.held)
        assertEquals(listOf(0), fake.writes)
    }
    @Test fun verifiedDisconnectedRouteDoesNotRepeatMutations() {
        val fake = Fake(0, 0)
        assertTrue(AudioProfileRouteController.hold(fake) {}.held)
        assertTrue(fake.writes.isEmpty())
    }
    @Test fun asynchronousDisconnectIsReportedAsUnconfirmed() {
        val fake = Fake().also { it.disconnects = false }
        assertFalse(AudioProfileRouteController.hold(fake) {}.held)
    }
    @Test fun rejectedPolicyIsAnExplicitFailure() {
        val fake = Fake().also { it.accepts = false }
        assertThrows(IllegalStateException::class.java) { AudioProfileRouteController.hold(fake) {} }
    }
    @Test fun successfulReturnWithoutActualPolicyChangeIsAnExplicitFailure() {
        val fake = Fake().also { it.applies = false }
        assertThrows(IllegalStateException::class.java) { AudioProfileRouteController.hold(fake) {} }
    }
    @Test fun unavailablePolicyDoesNotOverwriteRecoveryState() {
        val fake = Fake(-1)
        assertThrows(IllegalStateException::class.java) {
            AudioProfileRouteController.hold(fake) { fail("No valid original policy") }
        }
        assertTrue(fake.writes.isEmpty())
    }
    @Test fun optOutRestoresOriginalImmediately() {
        val fake = Fake(0, 0)
        assertEquals(100, AudioProfileRouteController.restore(fake, 100).policy)
        assertEquals(listOf(100), fake.writes)
    }
    @Test fun failedRestoreCannotBeReportedAsRestored() {
        val fake = Fake(0, 0).also { it.applies = false }
        assertThrows(IllegalStateException::class.java) { AudioProfileRouteController.restore(fake, 100) }
    }
    @Test fun failedRecoveryStorageDoesNotDisableAudio() {
        val fake = Fake()
        assertThrows(IllegalStateException::class.java) {
            AudioProfileRouteController.hold(fake) { error("Storage failed") }
        }
        assertTrue(fake.writes.isEmpty())
    }
}
