package me.kavishdevar.librepods.services

/** Completion belongs to an attempt, not just a Bluetooth connection. Reset rejects late IO results. */
internal class PhoneResumeRetry {
    private var sequence = 0L
    private var pending: Long? = null
    private var complete = false
    private var failures = 0
    @Volatile var retryAt = 0L; private set
    @Synchronized fun reset() { sequence++; pending = null; complete = false; failures = 0; retryAt = 0L }
    @Synchronized fun begin(now: Long): Long? {
        if (complete || pending != null || now < retryAt) return null
        return (++sequence).also { pending = it }
    }
    @Synchronized fun finish(token: Long, now: Long, success: Boolean): Boolean {
        if (pending != token) return false
        pending = null
        complete = success
        if (!success) {
            failures++
            retryAt = now + minOf(5_000L, 250L shl minOf(failures - 1, 5))
        }
        return true
    }
}
