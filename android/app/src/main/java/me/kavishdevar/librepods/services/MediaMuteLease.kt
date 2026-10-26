package me.kavishdevar.librepods.services

/** Save before mutating, and restore only the mute state owned by this experiment. */
internal class MediaMuteLease(private val audio: Audio, private val store: Store) {
    interface Audio { fun muted(): Boolean; fun setMuted(muted: Boolean) }
    interface Store { fun original(): Boolean?; fun save(original: Boolean); fun clear() }
    fun owned(): Boolean = store.original() != null
    fun hold() {
        if (!owned()) store.save(audio.muted())
        if (!audio.muted()) audio.setMuted(true)
        check(audio.muted()) { "Media mute was not applied by the system" }
    }
    fun restore() {
        val original = store.original() ?: return
        if (audio.muted() != original) audio.setMuted(original)
        check(audio.muted() == original) { "Media mute restore was not confirmed" }
        store.clear()
    }
}

internal object SilentHandoffDecision {
    enum class Action { RESTORE, HOLD, WAIT_FOR_DETACH, WAIT_FOR_AIRPODS, WAIT_FOR_ROUTE_INFO }
    fun decide(enabled: Boolean, session: Boolean, allowed: Boolean, owned: Boolean,
        bluetoothOutput: Boolean?, targetReady: Boolean): Action {
        if (!enabled || !session) return Action.RESTORE
        if (!allowed) return when (bluetoothOutput) {
            null -> Action.WAIT_FOR_ROUTE_INFO
            true -> Action.WAIT_FOR_DETACH
            false -> Action.HOLD
        }
        if (owned && !targetReady) return Action.WAIT_FOR_AIRPODS
        return Action.RESTORE
    }
}

internal object MediaRouteConfirmation {
    data class Output(val bluetooth: Boolean, val a2dp: Boolean, val address: String)
    fun targetReady(outputs: List<Output>?, target: String, a2dpConnected: Boolean): Boolean =
        a2dpConnected && target.isNotBlank() && !outputs.isNullOrEmpty() && outputs.all {
            it.bluetooth && it.a2dp && it.address.equals(target, true)
        }
}
