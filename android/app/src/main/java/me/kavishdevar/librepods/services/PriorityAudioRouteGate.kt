package me.kavishdevar.librepods.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit

/** Holds only this AirPods device's media/call profiles while the remote host owns audio.
 * The AACP control connection stays available. Saved connection policies are restored on opt-out.
 * Privileged Bluetooth permissions require the companion system module.
 */
internal class PriorityAudioRouteGate(private val context: Context) {
    private val prefs = context.getSharedPreferences("playback_priority_route", Context.MODE_PRIVATE)
    private var generation = 0L
    private val requested = mutableMapOf<String, Boolean>()
    private val generations = mutableMapOf<String, Long>()

    @SuppressLint("MissingPermission")
    @Synchronized fun update(device: BluetoothDevice, allowed: Boolean) {
        if (requested[device.address] == allowed) return
        if (context.checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") != PackageManager.PERMISSION_GRANTED) {
            Log.w("PlaybackPriority", "System media-route gate unavailable: install companion root module")
            return
        }
        requested[device.address] = allowed
        val current = ++generation
        generations[device.address] = current
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        for (profileId in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            val key = "${device.address}_$profileId"
            val bound = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        synchronized(this@PriorityAudioRouteGate) {
                            if (current != generations[device.address]) return
                            val get = proxy.javaClass.getMethod("getConnectionPolicy", BluetoothDevice::class.java)
                            val set = proxy.javaClass.getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                            val previous = get.invoke(proxy, device) as Int
                            if (!allowed) {
                                // Do not later enable a profile the user had already disabled.
                                if (previous > 0 && !prefs.contains(key)) prefs.edit(commit = true) { putInt(key, previous) }
                                if (previous > 0) {
                                    val result = set.invoke(proxy, device, 0)
                                    Log.i("PlaybackPriority", "Hold profile=$profile result=$result")
                                    if (result == false) requested.remove(device.address)
                                }
                            } else if (prefs.contains(key)) {
                                val original = prefs.getInt(key, 100)
                                val result = set.invoke(proxy, device, original)
                                if (result != false) prefs.edit(commit = true) { remove(key) }
                                else requested.remove(device.address)
                                Log.i("PlaybackPriority", "Restore profile=$profile policy=$original result=$result")
                            }
                        }
                    } catch (error: Exception) {
                        synchronized(this@PriorityAudioRouteGate) { requested.remove(device.address) }
                        Log.e("PlaybackPriority", "Profile gate failed for $profile; protection is not confirmed", error)
                    } finally {
                        adapter.closeProfileProxy(profile, proxy)
                    }
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, profileId)
            if (!bound) {
                requested.remove(device.address)
                Log.e("PlaybackPriority", "Unable to bind profile=$profileId; route protection is not confirmed")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized fun restore() {
        requested.clear()
        generations.clear() // Invalidate callbacks which have not applied their hold yet.
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        // Recover policies saved before a service/process restart too.
        val addresses = prefs.all.keys.mapNotNull { key -> key.substringBeforeLast('_').takeIf(BluetoothDeviceAddress::valid) }.distinct()
        for (address in addresses) update(adapter.getRemoteDevice(address), true)
    }
}

private object BluetoothDeviceAddress {
    fun valid(value: String): Boolean = value.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}"))
}
