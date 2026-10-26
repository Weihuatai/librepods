package me.kavishdevar.librepods.services

/** Never guess a baseline or erase the record before an acknowledged restoration. */
internal class SavedA2dpSuspendRecovery(private val store: Store, private val access: Access) {
    interface Store {
        fun original(): Boolean?
        fun clear()
    }
    fun interface Access { fun restore(original: Boolean): A2dpSuspendSnapshot }
    fun recover(): A2dpSuspendSnapshot? {
        val original = store.original() ?: return null
        val result = access.restore(original)
        check(result.external == original) { "External suspend value was not restored" }
        store.clear()
        return result
    }
}
