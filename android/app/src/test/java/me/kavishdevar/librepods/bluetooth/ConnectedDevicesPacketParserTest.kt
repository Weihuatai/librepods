package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class ConnectedDevicesPacketParserTest {
    private fun frame(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    @Test fun observedTwoPeerFrameLayoutKeepsAddressesAndFlags() {
        // Captured layout, with all personal addresses replaced by test fixtures.
        val data = frame("04 00 04 00 2e 00 01 00 02 aa bb cc dd ee 01 00 07 aa bb cc dd ee 02 02 01")
        val result = ConnectedDevicesPacketParser.parse(data)
        assertEquals(listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"), result.map { it.mac })
        assertEquals(0x07.toByte(), result[0].info2)
    }
    @Test fun shortHeaderCannotBeMistakenForAnEmptyPeerList() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectedDevicesPacketParser.parse(frame("04 00 04 00 2e 00 01 00"))
        }
    }
    @Test fun partialPeersAreRejectedInsteadOfReturningAFalseEmptyList() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectedDevicesPacketParser.parse(frame("04 00 04 00 2e 00 01 00 02 aa bb cc dd ee 01 00 07"))
        }
    }
    @Test fun unsignedCountIsNotInterpretedAsNoPeers() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectedDevicesPacketParser.parse(frame("04 00 04 00 2e 00 01 00 ff"))
        }
    }
    @Test fun completeEmptySnapshotRemainsValid() {
        assertTrue(ConnectedDevicesPacketParser.parse(frame("04 00 04 00 2e 00 01 00 00")).isEmpty())
    }
    @Test fun wrongOpcodeCannotChangePeerState() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectedDevicesPacketParser.parse(frame("04 00 04 00 0e 00 01 00 00"))
        }
    }
}
