package me.kavishdevar.librepods.services

import java.util.concurrent.atomic.AtomicReference

/** Reject work and replies from an earlier connection, including queued disconnect events. */
internal class ControlSessionEpoch {
    data class Snapshot(val generation: Long, val active: Boolean, val replies: Int)
    private val state = AtomicReference(Snapshot(0L, false, 0))
    private val actions = Any()

    // begin/runIfCurrent are for the IO lane. Reading or invalidating a session
    // must never wait for protocol callbacks, Bluetooth writes or another monitor.
    fun begin(): Long = synchronized(actions) {
        state.updateAndGet { Snapshot(it.generation + 1, true, 0) }.generation
    }
    fun invalidate(): Long = state.updateAndGet { Snapshot(it.generation + 1, false, 0) }.generation
    fun isCurrent(token: Long): Boolean = state.get().let { it.generation == token && it.active }
    fun isLatest(token: Long): Boolean = state.get().generation == token
    fun reply(token: Long): Boolean = update(token) { it.copy(replies = it.replies + 1) }
    fun finish(token: Long): Boolean = update(token) { it.copy(active = false) }
    private fun update(token: Long, change: (Snapshot) -> Snapshot): Boolean {
        while (true) {
            val before = state.get()
            if (before.generation != token || !before.active) return false
            if (state.compareAndSet(before, change(before))) return true
        }
    }
    fun runIfCurrent(token: Long, action: () -> Unit): Boolean = synchronized(actions) {
        if (!isCurrent(token)) return false
        action()
        return true
    }
    fun snapshot(): Snapshot = state.get()
}
