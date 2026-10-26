package me.kavishdevar.librepods.services

import java.util.concurrent.atomic.AtomicBoolean

/** Configure on the first actual peer reply, not after a timed head-tracking probe. */
internal class ControlChannelBootstrap {
    private val configured = AtomicBoolean(false)
    fun onReply(data: ByteArray): Boolean {
        val handshake = data.size >= 4 && data[0] == 0x01.toByte() && data[1] == 0.toByte() &&
            data[2] == 0x04.toByte() && data[3] == 0.toByte()
        val control = data.size >= 6 && data[0] == 0x04.toByte() && data[1] == 0.toByte() &&
            data[2] == 0x04.toByte() && data[3] == 0.toByte()
        return (handshake || control) && configured.compareAndSet(false, true)
    }
    fun needsHandshake() = !configured.get()
}
