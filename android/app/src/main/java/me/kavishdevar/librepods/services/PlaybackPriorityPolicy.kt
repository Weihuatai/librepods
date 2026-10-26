package me.kavishdevar.librepods.services

/** First admitted playback retains ownership until all observed audio is idle.
 * A rejected playback attempt never becomes evidence that the owner has stopped.
 * Unknown remote state fails closed, rather than assuming the other host is idle.
 */
internal class PlaybackPriorityPolicy {
    enum class Owner { UNKNOWN, IDLE, LOCAL, REMOTE }
    private var owner = Owner.UNKNOWN
    private var localActive = false
    private var idleStateKnown = false
    private val remoteActive = mutableSetOf<String>()
    private val explicitRemoteStreaming = mutableSetOf<String>()

    @Synchronized fun reset() {
        owner = Owner.UNKNOWN
        localActive = false
        idleStateKnown = false
        remoteActive.clear()
        explicitRemoteStreaming.clear()
    }

    /** A cached NONE/local source is not proof that the currently playing remote host is idle.
     * Local ownership is admitted only by a playback start after a confirmed idle state.
     */
    @Synchronized fun observeSource(address: String, local: Boolean, idle: Boolean, fresh: Boolean) {
        if (idle) {
            if (fresh) confirmIdle()
        } else if (!local) {
            remotePlayback(address, true)
        }
    }

    @Synchronized fun confirmIdle() {
        // A source-NONE transition cannot contradict an explicit hostStreamingState=YES.
        if (explicitRemoteStreaming.isNotEmpty()) return
        remoteActive.clear()
        idleStateKnown = true
        releaseIfIdle()
    }

    @Synchronized fun localPlayback(active: Boolean): Boolean {
        localActive = active
        if (!active) {
            releaseIfIdle()
            return true
        }
        if (owner == Owner.REMOTE || owner == Owner.UNKNOWN) return false
        owner = Owner.LOCAL
        idleStateKnown = true
        return true
    }

    @Synchronized fun remotePlayback(address: String, active: Boolean): Boolean {
        val key = address.uppercase()
        if (!active) {
            idleStateKnown = true
            remoteActive.remove(key)
            releaseIfIdle()
            return true
        }
        remoteActive.add(key)
        if (owner == Owner.LOCAL) return false
        owner = Owner.REMOTE
        idleStateKnown = true
        return true
    }

    @Synchronized fun remoteStreaming(address: String, active: Boolean): Boolean {
        val key = address.uppercase()
        if (active) explicitRemoteStreaming.add(key) else explicitRemoteStreaming.remove(key)
        return remotePlayback(address, active)
    }

    @Synchronized fun allowsLocal(): Boolean {
        return owner == Owner.IDLE || owner == Owner.LOCAL
    }

    @Synchronized fun protectsLocal(): Boolean {
        return owner == Owner.LOCAL
    }

    @Synchronized fun snapshot(): Owner = owner

    private fun releaseIfIdle() {
        if (idleStateKnown && !localActive && remoteActive.isEmpty()) {
            owner = Owner.IDLE
        }
    }
}
