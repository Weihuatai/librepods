package me.kavishdevar.librepods.services

/** Cached battery/name or a saved bond alone is never connection evidence. */
internal object ConnectionEvidence {
    fun fresh(ageMillis: Long?) = ageMillis != null && ageMillis in 0..5_000L
    fun mayAttemptAutomatically(confirmedControl: Boolean, aclConnected: Boolean,
        bleAgeMillis: Long?, worn: Boolean, docked: Boolean): Boolean {
        if (fresh(bleAgeMillis) && docked) return false
        return (confirmedControl && aclConnected) || (fresh(bleAgeMillis) && worn)
    }
    fun mayShowConnected(confirmedControl: Boolean, aclConnected: Boolean,
        bleAgeMillis: Long?, docked: Boolean): Boolean =
        confirmedControl && aclConnected && !(fresh(bleAgeMillis) && docked)
}
