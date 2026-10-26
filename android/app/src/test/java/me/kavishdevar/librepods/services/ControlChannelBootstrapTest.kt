package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class ControlChannelBootstrapTest {
    private fun frame(value: String) = value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    @Test fun capturedPeerHandshakeStartsConfigurationWithoutATimedProbe() {
        val b = ControlChannelBootstrap()
        assertTrue(b.onReply(frame("01 00 04 00 00 00 01 00 03 00 00 00 00 00 00 00 00 00")))
        assertFalse(b.needsHandshake())
    }
    @Test fun secondObservedReplyVariantIsRecognizedAsAPeerResponse() {
        assertTrue(ControlChannelBootstrap().onReply(frame("01 00 04 00 85 00 01 00 03 00 00 00 00 00 00 00 00 00")))
    }
    @Test fun alreadyInitializedPeerControlFrameCanConfigureAResumedSocket() {
        assertTrue(ControlChannelBootstrap().onReply(frame("04 00 04 00 09 00 0d 02 00 00 00")))
    }
    @Test fun repeatedRepliesDoNotRestartConfiguration() {
        val b = ControlChannelBootstrap()
        val reply = frame("01 00 04 00 00 00 01 00")
        assertTrue(b.onReply(reply))
        assertFalse(b.onReply(reply))
        assertFalse(b.onReply(frame("04 00 04 00 04 00")))
    }
    @Test fun unrelatedOrShortDataCannotMakeControlsReady() {
        val b = ControlChannelBootstrap()
        assertFalse(b.onReply(byteArrayOf(1, 0, 4)))
        assertFalse(b.onReply(frame("02 00 04 00 00 00")))
        assertTrue(b.needsHandshake())
    }
    @Test fun aNewConnectionMustConfigureIndependently() {
        val reply = frame("01 00 04 00 00 00 01 00")
        assertTrue(ControlChannelBootstrap().onReply(reply))
        assertTrue(ControlChannelBootstrap().onReply(reply))
    }
}
