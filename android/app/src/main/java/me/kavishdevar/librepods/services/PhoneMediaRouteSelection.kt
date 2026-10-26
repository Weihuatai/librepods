package me.kavishdevar.librepods.services

/** Unknown addresses and temporary speaker fallback are not evidence of a different Bluetooth sink. */
internal object PhoneMediaRouteSelection {
    data class Output(val bluetooth: Boolean, val a2dp: Boolean, val address: String)
    data class Selection(val target: Boolean, val otherBluetooth: Boolean)
    fun classify(outputs: List<Output>?, target: String): Selection {
        if (outputs.isNullOrEmpty()) return Selection(false, false)
        val targetSelected = outputs.all { it.a2dp && it.address.equals(target, true) && valid(target) }
        val otherSelected = outputs.any {
            it.bluetooth && !it.address.equals(target, true) && (!it.a2dp || valid(it.address))
        }
        return Selection(targetSelected, otherSelected)
    }
    private fun valid(address: String) = address.matches(Regex("(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"))
}
