package me.kavishdevar.librepods.services

/** Re-check the system, never treat a previous request as proof that the route is still held. */
internal object AudioProfileRouteController {
    interface Access {
        fun connectionPolicy(): Int
        fun connectionState(): Int
        fun setConnectionPolicy(policy: Int): Boolean
    }
    data class Observation(val policy: Int, val connectionState: Int) {
        val held: Boolean get() = policy == 0 && connectionState == 0
    }

    fun hold(access: Access, saveOriginal: (Int) -> Unit): Observation {
        val before = access.connectionPolicy()
        check(before >= 0) { "Connection policy unavailable: $before" }
        if (before > 0) saveOriginal(before)
        // A policy of zero alone is insufficient if the OEM has kept/reopened the profile.
        if (before != 0 || access.connectionState() != 0) {
            check(access.setConnectionPolicy(0)) { "System rejected connection policy 0" }
        }
        val after = access.connectionPolicy()
        check(after == 0) { "Connection policy was not held: $after" }
        return Observation(after, access.connectionState())
    }

    fun restore(access: Access, original: Int): Observation {
        if (access.connectionPolicy() != original) {
            check(access.setConnectionPolicy(original)) { "System rejected policy restore" }
        }
        val after = access.connectionPolicy()
        check(after == original) { "Connection policy restore not confirmed: $after" }
        return Observation(after, access.connectionState())
    }
}
