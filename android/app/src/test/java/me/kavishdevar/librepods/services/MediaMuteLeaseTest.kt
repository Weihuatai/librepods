package me.kavishdevar.librepods.services
import org.junit.Assert.*
import org.junit.Test
class MediaMuteLeaseTest {
    private class Fake(var mute: Boolean = false) : MediaMuteLease.Audio, MediaMuteLease.Store {
        var saved: Boolean? = null
        var accepts = true
        var writes = mutableListOf<Boolean>()
        val events = mutableListOf<String>()
        override fun muted() = mute
        override fun setMuted(muted: Boolean) { events.add("mute/$muted"); writes.add(muted); if(accepts) mute = muted }
        override fun original() = saved
        override fun save(original: Boolean) { events.add("save/$original"); saved = original }
        override fun clear() { saved = null }
        fun lease() = MediaMuteLease(this,this)
    }
    @Test fun muteIsSavedBeforeMutationAndRestoredWithoutVolumeIndexWrites() {
        val f=Fake();val lease=f.lease();lease.hold();assertTrue(f.mute);assertEquals(false,f.saved)
        lease.restore();assertFalse(f.mute);assertNull(f.saved);assertEquals(listOf(true,false),f.writes)
        assertEquals(listOf("save/false","mute/true","mute/false"),f.events)
    }
    @Test fun repeatedChecksDoNotOverwriteOriginalState() {
        val f=Fake();val lease=f.lease();repeat(5){lease.hold()}
        assertEquals(false,f.saved);assertEquals(listOf(true),f.writes)
    }
    @Test fun alreadyMutedUserIsNotUnmutedByHandoff() {
        val f=Fake(true);val lease=f.lease();lease.hold();lease.restore()
        assertTrue(f.mute);assertTrue(f.writes.isEmpty())
    }
    @Test fun noSavedLeaseMeansNoUnsolicitedUnmute() {
        val f=Fake(true);f.lease().restore();assertTrue(f.mute);assertTrue(f.writes.isEmpty())
    }
    @Test fun recoveryAfterProcessRestartUsesSavedOriginal() {
        val f=Fake();f.lease().hold();f.lease().restore();assertFalse(f.mute);assertNull(f.saved)
    }
    @Test fun systemRejectingMuteIsReportedInsteadOfClaimingSilence() {
        val f=Fake().also { it.accepts=false }
        assertThrows(IllegalStateException::class.java){f.lease().hold()};assertEquals(false,f.saved)
    }
    @Test fun failedRestoreKeepsRecoveryState() {
        val f=Fake();f.lease().hold();f.accepts=false
        assertThrows(IllegalStateException::class.java){f.lease().restore()};assertEquals(false,f.saved)
    }
    @Test fun storageFailureCannotLeaveAnUntrackedMute() {
        val f=Fake()
        val lease=MediaMuteLease(f,object:MediaMuteLease.Store {
            override fun original():Boolean?=null
            override fun save(original:Boolean){error("Storage failure")}
            override fun clear(){}
        })
        assertThrows(IllegalStateException::class.java){lease.hold()};assertTrue(f.writes.isEmpty())
    }
    @Test fun externallyRemovedMuteIsReassertedDuringProtection() {
        val f=Fake();val lease=f.lease();lease.hold();f.mute=false;lease.hold();assertTrue(f.mute)
    }
    @Test fun repeatedHandoffsRestoreAudioAfterEveryCycle() {
        val f=Fake();val lease=f.lease()
        repeat(4){lease.hold();assertTrue(f.mute);lease.restore();assertFalse(f.mute);assertNull(f.saved)}
    }

}
