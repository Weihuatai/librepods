package me.kavishdevar.librepods.bluetooth

/** Validate the complete frame before publishing any playback-state evidence. */
internal object AudioSourcePacketParser {
    data class Source(val mac: String, val type: Byte)
    fun parse(data: ByteArray): Source {
        require(data.size >= 13) { "Audio source frame needs 13 bytes" }
        require(data[4] == 0x0E.toByte()) { "Not an audio source frame" }
        require(data[12].toInt() in 0..2) { "Unknown audio source type" }
        val mac = data.sliceArray(6..11).reversedArray().joinToString(":") { "%02X".format(it) }
        return Source(mac, data[12])
    }
}
