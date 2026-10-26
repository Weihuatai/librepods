package me.kavishdevar.librepods.services

/** Route confirmation admits the first hold; temporary route changes cannot release an owned hold. */
internal object PhoneGuardHoldDecision {
    enum class Action { HOLD, WAIT, RESTORE }
    fun decide(enabled: Boolean, aclConnected: Boolean?, controlConfirmed: Boolean,
               remoteIdle: Boolean, leaseOwned: Boolean, mediaReady: Boolean): Action = when {
        !enabled || aclConnected == false || remoteIdle -> Action.RESTORE
        leaseOwned -> Action.HOLD
        mediaReady && (aclConnected == true || controlConfirmed) -> Action.HOLD
        else -> Action.WAIT
    }
}
