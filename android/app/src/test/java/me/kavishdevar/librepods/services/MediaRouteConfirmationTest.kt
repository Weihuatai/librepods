package me.kavishdevar.librepods.services
import org.junit.Assert.*
import org.junit.Test
class MediaRouteConfirmationTest {
    private val target="AA:BB:CC:DD:EE:FF"
    private val pods=MediaRouteConfirmation.Output(true,true,target)
    @Test fun matchingActiveAirpodsCanReleaseMute() {
        assertTrue(MediaRouteConfirmation.targetReady(listOf(pods),target.lowercase(),true))
    }
    @Test fun noOutputsCannotReleaseMute() {
        assertFalse(MediaRouteConfirmation.targetReady(emptyList(),target,true))
        assertFalse(MediaRouteConfirmation.targetReady(null,target,true))
    }
    @Test fun speakerAlongsideAirpodsCannotReleaseMute() {
        assertFalse(MediaRouteConfirmation.targetReady(listOf(pods,MediaRouteConfirmation.Output(false,false,"")),target,true))
    }
    @Test fun anotherBluetoothSinkCannotReleaseMute() {
        assertFalse(MediaRouteConfirmation.targetReady(listOf(pods.copy(address="OTHER")),target,true))
    }
    @Test fun missingAddressCannotBeAssumedToBeAirpods() {
        assertFalse(MediaRouteConfirmation.targetReady(listOf(pods.copy(address="")),target,true))
    }
    @Test fun connectedProfileWithoutAnActiveTargetRouteIsInsufficient() {
        assertFalse(MediaRouteConfirmation.targetReady(listOf(pods.copy(a2dp=false)),target,true))
    }
    @Test fun activeRouteWithoutVerifiedProfileCannotReleaseMute() {
        assertFalse(MediaRouteConfirmation.targetReady(listOf(pods),target,false))
    }
}
