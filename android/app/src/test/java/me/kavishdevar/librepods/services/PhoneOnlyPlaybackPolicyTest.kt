package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class PhoneOnlyPlaybackPolicyTest {
    private val mac = "AA:BB:CC:DD:EE:01"
    private val phone = "AA:BB:CC:DD:EE:02"
    @Test fun ownershipHintWaitsForPlaybackEvidenceWithoutInventingAnActivePlayer() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, false)
        p.expectRemote()
        assertTrue(p.shouldHold())
        assertFalse(p.hasBusyRemote())
        p.remote(mac, false)
        assertFalse(p.shouldHold())
    }
    @Test fun ownershipHintCannotEraseActualMacStreamingEvidence() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true)
        p.expectRemote()
        assertEquals(PhoneOnlyPlaybackPolicy.State.REMOTE_PLAYING, p.state())
    }
    @Test fun missingPeerListAndPhoneNoneDoNotMeanThereIsNoMac() {
        val p = PhoneOnlyPlaybackPolicy()
        p.source(phone, local = true, idle = true, fresh = true)
        assertFalse(p.hasPeerSnapshot())
        assertTrue(p.shouldHold())
    }
    @Test fun completeSingleDeviceSnapshotCanReleasePhoneOnlyUse() {
        val p = PhoneOnlyPlaybackPolicy()
        p.connectedDevices(emptyList())
        assertTrue(p.hasPeerSnapshot())
        assertFalse(p.shouldHold())
    }
    @Test fun peerPresenceWithoutPlaybackSnapshotRemainsUnknown() {
        val p = PhoneOnlyPlaybackPolicy()
        p.connectedDevices(listOf(mac))
        p.source(phone, local = true, idle = true, fresh = true)
        assertTrue(p.shouldHold())
    }
    @Test fun emptyPeerSnapshotCannotOverrideExplicitMacPlaying() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true)
        p.connectedDevices(emptyList())
        assertTrue(p.shouldHold())
        p.remote(mac, false)
        assertFalse(p.shouldHold())
    }
    @Test fun remoteStartOverridesPreviouslyIdlePhoneAdmission() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, false)
        assertFalse(p.shouldHold())
        p.remote(mac, true)
        assertTrue(p.shouldHold())
        assertEquals(PhoneOnlyPlaybackPolicy.State.REMOTE_PLAYING, p.state())
    }
    @Test fun phoneSourceAndPhoneNoneCannotReleasePlayingMac() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true)
        p.source(phone, local = true, idle = false, fresh = true)
        p.source(phone, local = true, idle = true, fresh = true)
        assertTrue(p.shouldHold())
    }
    @Test fun macIdleReleasesImmediatelyWithoutWaitingForPhoneToStop() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true)
        p.source(mac, local = false, idle = true, fresh = true)
        assertFalse(p.shouldHold())
    }
    @Test fun unknownAndCachedIdleDoNotAdmitAnAttemptedTakeover() {
        val p = PhoneOnlyPlaybackPolicy()
        p.source(phone, local = true, idle = true, fresh = false)
        assertTrue(p.shouldHold())
        p.source(mac, local = false, idle = true, fresh = true)
        assertFalse(p.shouldHold())
    }
    @Test fun anotherHostsIdleCannotReleaseBusyMac() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true)
        p.source("AA:BB:CC:DD:EE:03", local = false, idle = true, fresh = true)
        assertTrue(p.shouldHold())
        p.remote(mac.lowercase(), false)
        assertFalse(p.shouldHold())
    }
    @Test fun allReportedRemotePlayersMustStop() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true); p.remote("OTHER", true)
        p.remote(mac, false)
        assertTrue(p.shouldHold())
        p.remote("OTHER", false)
        assertFalse(p.shouldHold())
    }
    @Test fun staleSessionEvidenceIsNotRetained() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true); p.reset()
        assertEquals(PhoneOnlyPlaybackPolicy.State.UNKNOWN, p.state())
        assertFalse(p.hasBusyRemote())
    }
    @Test fun unaddressedNoneCannotReleaseAMac() {
        val p = PhoneOnlyPlaybackPolicy()
        p.remote(mac, true)
        p.source("00:00:00:00:00:00", local = false, idle = true, fresh = true)
        assertTrue(p.shouldHold())
    }
}
