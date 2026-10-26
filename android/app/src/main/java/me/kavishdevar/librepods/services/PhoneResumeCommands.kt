package me.kavishdevar.librepods.services

/** Never continue an ownership claim after a failed send or a newly playing remote host. */
internal object PhoneResumeCommands {
    data class Result(val own: Boolean, val media: Boolean, val hijack: Boolean) {
        val success get() = own && media && hijack
    }
    fun send(allowed: () -> Boolean, own: () -> Boolean, media: () -> Boolean, hijack: () -> Boolean): Result {
        val owned = allowed() && own()
        val informed = owned && allowed() && media()
        val claimed = informed && allowed() && hijack()
        return Result(owned, informed, claimed)
    }
}
