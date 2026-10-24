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

    @Synchronized fun reset() {
        owner = Owner.UNKNOWN
        localActive = false
        idleStateKnown = false
        remoteActive.clear()
    }

    @Synchronized fun seedLocalPlayback() {
        if (owner != Owner.UNKNOWN) return
        owner = Owner.LOCAL
        idleStateKnown = true
        localActive = true
    }

    @Synchronized fun confirmIdle() {
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
