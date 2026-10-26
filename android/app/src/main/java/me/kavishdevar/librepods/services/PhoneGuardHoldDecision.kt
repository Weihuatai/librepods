package me.kavishdevar.librepods.services

/** Route confirmation admits the first hold; temporary route changes cannot release an owned hold. */
internal object PhoneGuardHoldDecision {
    enum class Action { HOLD, WAIT, RESTORE }
    fun decide(enabled: Boolean, aclConnected: Boolean?, controlConfirmed: Boolean,
               remoteIdle: Boolean, leaseOwned: Boolean, mediaReady: Boolean,
               otherBluetoothSelected: Boolean = false): Action = when {
        !enabled || aclConnected == false || remoteIdle || otherBluetoothSelected -> Action.RESTORE
        leaseOwned -> Action.HOLD
        mediaReady && (aclConnected == true || controlConfirmed) -> Action.HOLD
        else -> Action.WAIT
    }
}
