package me.kavishdevar.librepods.services

/** Reject work and replies from an earlier connection, including queued disconnect events. */
internal class ControlSessionEpoch {
    data class Snapshot(val generation: Long, val active: Boolean, val replies: Int)
    private var generation = 0L
    private var active = false
    private var replies = 0
    @Synchronized fun begin(): Long {
        generation++
        active = true
        replies = 0
        return generation
    }
    @Synchronized fun invalidate(): Long {
        generation++
        active = false
        replies = 0
        return generation
    }
    @Synchronized fun isCurrent(token: Long): Boolean = token == generation && active
    @Synchronized fun isLatest(token: Long): Boolean = token == generation
    @Synchronized fun reply(token: Long): Boolean {
        if (!isCurrent(token)) return false
        replies++
        return true
    }
    @Synchronized fun finish(token: Long): Boolean {
        if (!isCurrent(token)) return false
        active = false
        return true
    }
    @Synchronized fun runIfCurrent(token: Long, action: () -> Unit): Boolean {
        if (!isCurrent(token)) return false
        action()
        return true
    }
    @Synchronized fun snapshot() = Snapshot(generation, active, replies)
}
