package me.kavishdevar.librepods.services

/** Protocol core shared by the root helper and regression tests; PING never runs diagnostics. */
internal class A2dpSuspendSession(private val original: Boolean, private val access: Access) {
    interface Access {
        fun set(suspended: Boolean)
        fun snapshot(): A2dpSuspendSnapshot
    }
    private var closed = false
    @Synchronized fun command(value: String): String {
        check(!closed) { "Suspend session closed" }
        if (value == "PING") return "PONG"
        if (value == "REASSERT") { access.set(true); return "HELD" }
        val state = when (value) {
            "HOLD" -> { access.set(true); access.snapshot() }
            "ALLOW" -> { access.set(original); access.snapshot() }
            "CHECK" -> access.snapshot().let {
                if (!it.external || !it.applied) { access.set(true); access.snapshot() } else it
            }
            else -> error("Invalid command")
        }
        return "STATE ${state.external} ${state.internal} ${state.applied}"
    }
    @Synchronized fun close() {
        if (closed) return
        // Keep the recovery retryable if the system call itself fails.
        access.set(original)
        closed = true
    }
}
