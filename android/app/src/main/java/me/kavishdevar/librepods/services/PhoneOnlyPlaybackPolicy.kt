package me.kavishdevar.librepods.services

/** Remote playback always wins. Phone activity is never evidence that a remote host stopped. */
internal class PhoneOnlyPlaybackPolicy {
    enum class State { UNKNOWN, REMOTE_PLAYING, REMOTE_IDLE }
    private val busy = mutableSetOf<String>()
    private var state = State.UNKNOWN
    private var peersKnown = false
    private var peers = emptySet<String>()
    private var ownershipHintSeen = false
    private var missingBusy = emptySet<String>()
    private var peerConfirmationRequested = false

    @Synchronized fun reset() {
        busy.clear(); state = State.UNKNOWN; peersKnown = false; peers = emptySet(); ownershipHintSeen = false
        missingBusy = emptySet(); peerConfirmationRequested = false
    }
    @Synchronized fun connectedDevices(remoteAddresses: List<String>) {
        peersKnown = true
        peers = remoteAddresses.map { it.uppercase() }.toSet()
        val absent = busy - peers
        // One empty snapshot during a handoff is insufficient. The service asks
        // for another full snapshot; only repeated absence clears stale playback.
        val confirmedAbsent = absent.isNotEmpty() && absent == missingBusy && peerConfirmationRequested
        if (confirmedAbsent) {
            busy.removeAll(absent)
            missingBusy = emptySet(); peerConfirmationRequested = false
            if (busy.isEmpty() && peers.isEmpty()) ownershipHintSeen = false
        } else if (absent != missingBusy) {
            missingBusy = absent; peerConfirmationRequested = false
        }
        state = when {
            busy.isNotEmpty() -> State.REMOTE_PLAYING
            peers.isEmpty() -> State.REMOTE_IDLE
            confirmedAbsent -> State.UNKNOWN
            else -> state
        }
    }
    @Synchronized fun hasPeerSnapshot() = peersKnown
    @Synchronized fun needsPeerConfirmation() = missingBusy.isNotEmpty()
    @Synchronized fun peerConfirmationRequestSent() {
        if (missingBusy.isNotEmpty()) peerConfirmationRequested = true
    }
    @Synchronized fun expectRemote(request: Boolean = false) {
        if (request) ownershipHintSeen = true
        if (busy.isEmpty()) state = State.UNKNOWN
    }
    @Synchronized fun remote(address: String, active: Boolean) {
        missingBusy = emptySet(); peerConfirmationRequested = false
        if (active) busy.add(address.uppercase()) else busy.remove(address.uppercase())
        state = if (busy.isEmpty()) State.REMOTE_IDLE else State.REMOTE_PLAYING
    }
    @Synchronized fun source(address: String, local: Boolean, idle: Boolean, fresh: Boolean) {
        if (!idle && !local) remote(address, true)
        // An idle report must identify the stopped remote host. A phone/NONE report
        // produced during an attempted takeover must not release a busy Mac.
        else if (idle && fresh && address == "00:00:00:00:00:00" && peersKnown &&
            busy.isEmpty() && !ownershipHintSeen && state == State.UNKNOWN) {
            // The observed initial all-idle snapshot uses a zero address. It is
            // not admitted during ownership handoff or against Mac-playing evidence.
            state = State.REMOTE_IDLE
        } else if (idle && fresh && !local) {
            if (address.uppercase() in busy || (busy.isEmpty() && valid(address))) remote(address, false)
        } else if (idle && fresh && local && busy.isEmpty() && peersKnown && peers.isEmpty()) state = State.REMOTE_IDLE
    }
    @Synchronized fun state(): State = state
    @Synchronized fun shouldHold() = state != State.REMOTE_IDLE
    @Synchronized fun hasBusyRemote() = busy.isNotEmpty()
    private fun valid(address: String) = address.matches(Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) &&
        address != "00:00:00:00:00:00" && address != "FF:FF:FF:FF:FF:FF"
}
