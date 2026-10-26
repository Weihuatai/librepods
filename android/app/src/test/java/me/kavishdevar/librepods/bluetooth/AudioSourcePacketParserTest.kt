package me.kavishdevar.librepods.bluetooth
import org.junit.Assert.*
import org.junit.Test
class AudioSourcePacketParserTest {
    private fun packet(type: Int = 2) = byteArrayOf(4,0,4,0,14,0,1,2,3,4,5,6,type.toByte())
    @Test fun allTruncatedFramesAreRejectedBeforeStatePublication() {
        for (size in 0..12) assertThrows(IllegalArgumentException::class.java) {
            AudioSourcePacketParser.parse(packet().copyOf(size))
        }
    }
    @Test fun hostAddressIsDecodedInProtocolOrder() {
        assertEquals("06:05:04:03:02:01", AudioSourcePacketParser.parse(packet()).mac)
    }
    @Test fun explicitNoneIsPreservedWithoutAddingAnyIdleDelay() {
        assertEquals(0.toByte(),AudioSourcePacketParser.parse(packet(0)).type)
    }
    @Test fun unrelatedOpcodeCannotReuseThePreviousAudioSource() {
        val frame=packet();frame[4]=17
        assertThrows(IllegalArgumentException::class.java) { AudioSourcePacketParser.parse(frame) }
    }
    @Test fun unknownSourceTypeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { AudioSourcePacketParser.parse(packet(3)) }
    }
}
