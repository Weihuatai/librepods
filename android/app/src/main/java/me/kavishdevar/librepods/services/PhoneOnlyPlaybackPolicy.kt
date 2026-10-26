package me.kavishdevar.librepods.services

/** Remote playback always wins. Phone activity is never evidence that a remote host stopped. */
internal class PhoneOnlyPlaybackPolicy {
    enum class State { UNKNOWN, REMOTE_PLAYING, REMOTE_IDLE }
    private val busy = mutableSetOf<String>()
    private var state = State.UNKNOWN

    @Synchronized fun reset() { busy.clear(); state = State.UNKNOWN }
    @Synchronized fun remote(address: String, active: Boolean) {
        if (active) busy.add(address.uppercase()) else busy.remove(address.uppercase())
        state = if (busy.isEmpty()) State.REMOTE_IDLE else State.REMOTE_PLAYING
    }
    @Synchronized fun source(address: String, local: Boolean, idle: Boolean, fresh: Boolean) {
        if (!idle && !local) remote(address, true)
        // An idle report must identify the stopped remote host. A phone/NONE report
        // produced during an attempted takeover must not release a busy Mac.
        else if (idle && fresh && !local) {
            if (address.uppercase() in busy || (busy.isEmpty() && valid(address))) remote(address, false)
        } else if (idle && fresh && local && busy.isEmpty()) state = State.REMOTE_IDLE
    }
    @Synchronized fun state(): State = state
    @Synchronized fun shouldHold() = state != State.REMOTE_IDLE
    @Synchronized fun hasBusyRemote() = busy.isNotEmpty()
    private fun valid(address: String) = address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) &&
        address != "00:00:00:00:00:00" && address != "FF:FF:FF:FF:FF:FF"
}
