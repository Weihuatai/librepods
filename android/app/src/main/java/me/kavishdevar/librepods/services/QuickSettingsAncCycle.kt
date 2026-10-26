package me.kavishdevar.librepods.services

import me.kavishdevar.librepods.data.NoiseControlMode

/** The quick-settings button deliberately skips Off, which earbuds may reject. */
internal object QuickSettingsAncCycle {
    private val modes = listOf(
        NoiseControlMode.TRANSPARENCY.ordinal + 1,
        NoiseControlMode.ADAPTIVE.ordinal + 1,
        NoiseControlMode.NOISE_CANCELLATION.ordinal + 1,
    )

    fun next(currentMode: Int): Int = modes[(modes.indexOf(currentMode) + 1) % modes.size]
}
