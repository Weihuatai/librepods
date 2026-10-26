package me.kavishdevar.librepods.services

internal data class A2dpSuspendSnapshot(val external: Boolean, val internal: Boolean, val applied: Boolean) {
    companion object {
        fun parse(text: String): A2dpSuspendSnapshot? {
            val match = Regex("Bluetooth A2DP suspended, requested ext: (true|false), requested int: (true|false), applied (true|false)")
                .find(text) ?: return null
            return A2dpSuspendSnapshot(match.groupValues[1].toBoolean(), match.groupValues[2].toBoolean(),
                match.groupValues[3].toBoolean())
        }
    }
}
