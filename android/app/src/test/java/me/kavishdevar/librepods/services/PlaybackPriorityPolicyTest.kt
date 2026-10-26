package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class PlaybackPriorityPolicyTest {
    private fun idle(): PlaybackPriorityPolicy = PlaybackPriorityPolicy().also {
        it.confirmIdle()
        assertTrue(it.allowsLocal())
    }

    @Test fun unknownStateCannotStealFromAnUnobservedMac() {
        val p = PlaybackPriorityPolicy()
        assertFalse(p.localPlayback(true))
        assertFalse(p.allowsLocal())
    }
    @Test fun localSilenceIsNotProofThatAnUnobservedMacIsIdle() {
        val p = PlaybackPriorityPolicy()
        p.localPlayback(false)
        assertFalse(p.allowsLocal())
        assertFalse(p.localPlayback(true))
    }
    @Test fun macMusicBlocksEveryLocalPlaybackAttempt() {
        val p = idle()
        assertTrue(p.remotePlayback("MAC", true))
        assertFalse(p.localPlayback(true))
        assertFalse(p.localPlayback(true))
        assertEquals(PlaybackPriorityPolicy.Owner.REMOTE, p.snapshot())
    }
    @Test fun androidMusicDoesNotYieldToRemoteStart() {
        val p = idle()
        assertTrue(p.localPlayback(true))
        assertFalse(p.remotePlayback("MAC", true))
        assertTrue(p.protectsLocal())
    }
    @Test fun macPauseImmediatelyAllowsPhonePlayback() {
        val p = idle()
        p.remotePlayback("MAC", true)
        p.remotePlayback("MAC", false)
        assertTrue(p.allowsLocal())
        assertTrue(p.localPlayback(true))
        assertEquals(PlaybackPriorityPolicy.Owner.LOCAL, p.snapshot())
    }
    @Test fun androidPauseImmediatelyAllowsMacPlayback() {
        val p = idle()
        p.localPlayback(true)
        p.localPlayback(false)
        assertEquals(PlaybackPriorityPolicy.Owner.IDLE, p.snapshot())
        assertTrue(p.remotePlayback("MAC", true))
        assertEquals(PlaybackPriorityPolicy.Owner.REMOTE, p.snapshot())
    }
    @Test fun explicitNoAudioSourceImmediatelyReleasesRemoteOwner() {
        val p = idle()
        p.remotePlayback("MAC", true)
        p.confirmIdle()
        assertTrue(p.allowsLocal())
        assertTrue(p.localPlayback(true))
    }
    @Test fun rejectedPhoneAttemptCannotClearRemoteBusyState() {
        val p = idle()
        p.remotePlayback("MAC", true)
        assertFalse(p.localPlayback(true))
        p.localPlayback(false)
        assertFalse(p.allowsLocal())
    }
    @Test fun waitingMacCanProceedWhenPhoneOwnerStops() {
        val p = idle()
        p.localPlayback(true)
        assertFalse(p.remotePlayback("MAC", true))
        p.localPlayback(false)
        assertEquals(PlaybackPriorityPolicy.Owner.REMOTE, p.snapshot())
        p.remotePlayback("MAC", false)
        assertEquals(PlaybackPriorityPolicy.Owner.IDLE, p.snapshot())
    }
    @Test fun waitingPhoneIsAdmittedImmediatelyWhenMacStops() {
        val p = idle()
        p.remotePlayback("MAC", true)
        assertFalse(p.localPlayback(true))
        p.remotePlayback("MAC", false)
        assertTrue(p.allowsLocal())
        assertTrue(p.protectsLocal())
        p.localPlayback(false)
        assertTrue(p.allowsLocal())
    }
    @Test fun explicitRemoteIdleCanResolveUnknownStateWithoutADelay() {
        val p = PlaybackPriorityPolicy()
        p.remotePlayback("MAC", false)
        assertTrue(p.allowsLocal())
        assertTrue(p.localPlayback(true))
    }
    @Test fun noAudioSourceDoesNotOverrideObservedLocalPlayback() {
        val p = idle()
        p.localPlayback(true)
        p.confirmIdle()
        assertTrue(p.protectsLocal())
        assertFalse(p.remotePlayback("MAC", true))
    }
    @Test fun localSourcePacketCannotOverwriteProtectedMac() {
        val p = idle()
        p.remotePlayback("MAC", true)
        p.observeSource("PHONE", local = true, idle = false, fresh = true)
        assertFalse(p.allowsLocal())
    }
    @Test fun cachedPhoneSourceCannotAdmitBackgroundPhonePlayback() {
        val p = PlaybackPriorityPolicy()
        p.observeSource("PHONE", local = true, idle = false, fresh = false)
        assertFalse(p.localPlayback(true))
        assertFalse(p.protectsLocal())
        assertTrue(p.remotePlayback("MAC", true))
    }
    @Test fun cachedNoneCannotAdmitPhoneBeforeCurrentMacStateArrives() {
        val p = PlaybackPriorityPolicy()
        p.observeSource("NONE", local = false, idle = true, fresh = false)
        assertFalse(p.localPlayback(true))
        assertTrue(p.remotePlayback("MAC", true))
        assertEquals(PlaybackPriorityPolicy.Owner.REMOTE, p.snapshot())
    }
    @Test fun freshLocalSourceAloneDoesNotProveEarlierMacPlaybackStopped() {
        val p = PlaybackPriorityPolicy()
        p.observeSource("PHONE", local = true, idle = false, fresh = true)
        assertFalse(p.protectsLocal())
        assertFalse(p.localPlayback(true))
    }
    @Test fun freshNoneAdmitsANewLocalStartImmediately() {
        val p = PlaybackPriorityPolicy()
        p.observeSource("NONE", local = false, idle = true, fresh = true)
        assertTrue(p.localPlayback(true))
        assertTrue(p.protectsLocal())
    }
    @Test fun cachedRemotePlaybackFailsClosedUntilExplicitIdle() {
        val p = PlaybackPriorityPolicy()
        p.observeSource("MAC", local = false, idle = false, fresh = false)
        assertFalse(p.allowsLocal())
        p.observeSource("NONE", local = false, idle = true, fresh = true)
        assertTrue(p.allowsLocal())
    }
    @Test fun resetDoesNotReusePreviousSessionOwnership() {
        val p = idle()
        p.localPlayback(true)
        p.reset()
        assertFalse(p.allowsLocal())
        assertFalse(p.protectsLocal())
    }
    @Test fun allReportedRemoteStreamsMustStopBeforeImmediateRelease() {
        val p = idle()
        p.remotePlayback("MAC", true)
        p.remotePlayback("OTHER", true)
        p.remotePlayback("mac", false)
        assertFalse(p.allowsLocal())
        p.remotePlayback("OTHER", false)
        assertTrue(p.allowsLocal())
    }
    @Test fun sourceNoneCannotReleaseAnExplicitlyStreamingMac() {
        val p = idle()
        p.remoteStreaming("MAC", true)
        p.observeSource("NONE", local = false, idle = true, fresh = true)
        assertFalse(p.allowsLocal())
        assertFalse(p.localPlayback(true))
    }
    @Test fun explicitMacStopImmediatelyReleasesAfterTransientNone() {
        val p = idle()
        p.remoteStreaming("MAC", true)
        p.observeSource("NONE", local = false, idle = true, fresh = true)
        p.remoteStreaming("MAC", false)
        assertTrue(p.localPlayback(true))
    }

    @Test fun continuousWaitingPhonePlaybackCanBecomeOwnerWithoutRestarting() {
        val p=idle();p.remoteStreaming("MAC",true)
        repeat(5){ assertFalse(p.localPlayback(true)) }
        p.remoteStreaming("MAC",false)
        assertTrue(p.protectsLocal());assertTrue(p.allowsLocal())
    }
    @Test fun stoppingWaitingPhoneBeforeMacPauseDoesNotAutoplayIt() {
        val p=idle();p.remoteStreaming("MAC",true);p.localPlayback(true);p.localPlayback(false)
        p.remoteStreaming("MAC",false)
        assertEquals(PlaybackPriorityPolicy.Owner.IDLE,p.snapshot())
    }
    @Test fun queuedPhoneStillWaitsForAllRemoteHostsToStop() {
        val p=idle();p.remoteStreaming("MAC",true);p.remoteStreaming("OTHER",true);p.localPlayback(true)
        p.remoteStreaming("MAC",false);assertFalse(p.allowsLocal())
        p.remoteStreaming("OTHER",false);assertTrue(p.protectsLocal())
    }

}
