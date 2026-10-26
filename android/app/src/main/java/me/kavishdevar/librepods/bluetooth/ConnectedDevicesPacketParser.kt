package me.kavishdevar.librepods.bluetooth

/** Only a complete, validated snapshot may establish that no other device is connected. */
internal object ConnectedDevicesPacketParser {
    data class Entry(val mac: String, val info1: Byte, val info2: Byte)
    fun parse(data: ByteArray): List<Entry> {
        require(data.size >= 9) { "Connected-device frame needs a nine-byte header" }
        require(data[4] == 0x2E.toByte()) { "Not a connected-device frame" }
        val count = data[8].toInt() and 0xFF
        require(data.size >= 9 + count * 8) { "Truncated connected-device snapshot" }
        return List(count) { index ->
            val offset = 9 + index * 8
            Entry(data.sliceArray(offset until offset + 6).joinToString(":") { "%02X".format(it) },
                data[offset + 6], data[offset + 7])
        }
    }
}
