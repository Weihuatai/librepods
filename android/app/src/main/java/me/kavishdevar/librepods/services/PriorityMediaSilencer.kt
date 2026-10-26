package me.kavishdevar.librepods.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Mute phone media only after Bluetooth detaches; never send mute to a remote absolute-volume sink. */
internal class PriorityMediaSilencer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("playback_priority_media_mute", Context.MODE_PRIVATE)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val lease = MediaMuteLease(object : MediaMuteLease.Audio {
        override fun muted() = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        override fun setMuted(muted: Boolean) = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }, object : MediaMuteLease.Store {
        override fun original(): Boolean? = if (prefs.contains("original")) prefs.getBoolean("original", false) else null
        override fun save(original: Boolean) {
            check(prefs.edit().putBoolean("original", original).commit()) { "Cannot save media mute recovery state" }
        }
        override fun clear() { check(prefs.edit().remove("original").commit()) { "Cannot clear media mute recovery state" } }
    })
    @Volatile var action = SilentHandoffDecision.Action.RESTORE; private set
    @Volatile var error: String? = null; private set
    @Volatile var muted = false; private set
    fun owned() = lease.owned()

    /** Returns true only when the current media output is this AirPods and no speaker is selected. */
    private fun routes(): List<AudioDeviceInfo>? = try {
        audio.getAudioDevicesForAttributes(attributes).takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
    private fun isBluetooth(device: AudioDeviceInfo) = device.type in setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID)

    fun update(enabled: Boolean, session: Boolean, allowed: Boolean, target: String, a2dpConnected: Boolean): Boolean {
        val devices = routes()
        val bluetooth = devices?.any { isBluetooth(it) }
        val targetReady = MediaRouteConfirmation.targetReady(devices?.map {
            MediaRouteConfirmation.Output(isBluetooth(it), it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, it.address)
        }, target, a2dpConnected)
        action = SilentHandoffDecision.decide(enabled, session, allowed, lease.owned(), bluetooth, targetReady)
        error = null
        try {
            when (action) {
                SilentHandoffDecision.Action.RESTORE -> lease.restore()
                SilentHandoffDecision.Action.HOLD -> lease.hold()
                SilentHandoffDecision.Action.WAIT_FOR_AIRPODS -> {
                    // Keep an existing lease silent while Bluetooth reconnects. Do not change
                    // remote absolute volume if an unrelated Bluetooth sink is selected.
                    if (bluetooth == false) lease.hold()
                }
                else -> Unit
            }
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        } catch (failure: Exception) {
            error = "${failure.javaClass.simpleName}: ${failure.message.orEmpty().take(150)}"
        }
        return enabled && session && (error != null || action in setOf(
            SilentHandoffDecision.Action.WAIT_FOR_DETACH,
            SilentHandoffDecision.Action.WAIT_FOR_AIRPODS,
            SilentHandoffDecision.Action.WAIT_FOR_ROUTE_INFO))
    }
    fun restore() = update(false, false, true, "", false)
}
