package me.kavishdevar.librepods.services

/** Heartbeats are cheap; full system diagnostics are reserved for transitions and sparse audits. */
internal object PhoneTransportSchedule {
    const val HEARTBEAT_MS = 5_000L
    const val VERIFY_MS = 30_000L
    const val COMMAND_WAKE_MS = 10_000L
    fun command(hold: Boolean, ownsLease: Boolean, applied: Boolean?, now: Long,
                lastCommand: Long, lastVerified: Long, force: Boolean): String? = when {
        !hold -> if (ownsLease || applied != null) "ALLOW" else null
        applied != true -> "HOLD"
        now - lastVerified >= VERIFY_MS -> "CHECK"
        force -> "REASSERT"
        now - lastCommand >= HEARTBEAT_MS -> "PING"
        else -> null
    }
}
