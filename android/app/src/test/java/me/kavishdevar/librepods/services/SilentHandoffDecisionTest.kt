package me.kavishdevar.librepods.services
import org.junit.Assert.*
import org.junit.Test
import me.kavishdevar.librepods.services.SilentHandoffDecision.Action.*
class SilentHandoffDecisionTest {
    @Test fun macBusyWithPhoneSpeakerRouteMeansSilentPlayback() {
        assertEquals(HOLD,SilentHandoffDecision.decide(true,true,false,false,false,false))
    }
    @Test fun doNotSendStreamMuteToEarbudsWhileMacOwnsThem() {
        assertEquals(WAIT_FOR_DETACH,SilentHandoffDecision.decide(true,true,false,false,true,false))
    }
    @Test fun unknownOutputDoesNotClaimSafeMuting() {
        assertEquals(WAIT_FOR_ROUTE_INFO,SilentHandoffDecision.decide(true,true,false,false,null,false))
    }
    @Test fun macPauseKeepsPhoneSilentUntilEarbudsAreReady() {
        assertEquals(WAIT_FOR_AIRPODS,SilentHandoffDecision.decide(true,true,true,true,false,false))
    }
    @Test fun connectedWrongBluetoothDeviceDoesNotReleaseMute() {
        assertEquals(WAIT_FOR_AIRPODS,SilentHandoffDecision.decide(true,true,true,true,true,false))
    }
    @Test fun confirmedTargetRouteRestoresOriginalMuteState() {
        assertEquals(RESTORE,SilentHandoffDecision.decide(true,true,true,true,true,true))
    }
    @Test fun disablingExperimentRestoresOwnedMuteEvenWithoutEarbuds() {
        assertEquals(RESTORE,SilentHandoffDecision.decide(false,false,false,true,null,false))
    }
    @Test fun normalPhonePlaybackOutsideProtectionIsUntouched() {
        assertEquals(RESTORE,SilentHandoffDecision.decide(true,false,false,false,false,false))
    }
}
