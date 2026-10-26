package me.kavishdevar.librepods.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import java.lang.reflect.InvocationTargetException

/** Audits actual profile policies and connection state, and restores only policies we changed. */
internal class PriorityAudioRouteGate(private val context: Context) {
    data class Status(val policy: Int? = null, val state: Int? = null, val error: String? = null, val appliedAllowed: Boolean? = null)
    private val prefs = context.getSharedPreferences("playback_priority_route", Context.MODE_PRIVATE)
    private var generation = 0L
    private var sequence = 0L
    private val desired = mutableMapOf<String, Boolean>()
    private val generations = mutableMapOf<String, Long>()
    private data class Pending(val token: Long, val since: Long)
    private val pending = mutableMapOf<String, Pending>()
    private val statuses = mutableMapOf<Int, Status>()

    @Synchronized fun snapshot(): Map<Int, Status> = statuses.toMap()

    @SuppressLint("MissingPermission")
    @Synchronized fun update(device: BluetoothDevice, allowed: Boolean) {
        if (context.checkSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") != PackageManager.PERMISSION_GRANTED) {
            for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
                statuses[profile] = Status(error = "BLUETOOTH_PRIVILEGED permission missing")
            }
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (desired[device.address] != allowed) {
            desired[device.address] = allowed
            generations[device.address] = ++generation
            // A changed decision must supersede pending work immediately.
            pending.keys.removeAll { it.startsWith("${device.address}_") }
        }
        val current = generations.getValue(device.address)
        for (profileId in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            val key = "${device.address}_$profileId"
            val now = SystemClock.elapsedRealtime()
            val previousPending = pending[key]
            if (previousPending != null && now - previousPending.since < 5_000L) continue
            val token = ++sequence
            pending[key] = Pending(token, now)
            val bound = try { adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        synchronized(this@PriorityAudioRouteGate) {
                            if (current != generations[device.address] || token != pending[key]?.token) return
                            val get = proxy.javaClass.getMethod("getConnectionPolicy", BluetoothDevice::class.java)
                            val set = proxy.javaClass.getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                            val access = object : AudioProfileRouteController.Access {
                                override fun connectionPolicy() = get.invoke(proxy, device) as Int
                                override fun connectionState() = proxy.getConnectionState(device)
                                override fun setConnectionPolicy(policy: Int) = set.invoke(proxy, device, policy) == true
                            }
                            val observed = if (!allowed) {
                                AudioProfileRouteController.hold(access) { original ->
                                    if (!prefs.contains(key)) {
                                        check(prefs.edit().putInt(key, original).commit()) { "Cannot save original connection policy" }
                                    }
                                }
                            } else if (prefs.contains(key)) {
                                AudioProfileRouteController.restore(access, prefs.getInt(key, 100)).also {
                                    prefs.edit(commit = true) { remove(key) }
                                }
                            } else {
                                AudioProfileRouteController.Observation(access.connectionPolicy(), access.connectionState())
                            }
                            val next = Status(observed.policy, observed.connectionState, appliedAllowed = allowed)
                            if (statuses[profile] != next) {
                                Log.i("PlaybackPriority", "Verified profile=$profile allowed=$allowed policy=${observed.policy} state=${observed.connectionState}")
                            }
                            statuses[profile] = next
                        }
                    } catch (error: Exception) {
                        val cause = (error as? InvocationTargetException)?.targetException ?: error
                        synchronized(this@PriorityAudioRouteGate) {
                            if (current == generations[device.address] && token == pending[key]?.token) {
                                val next = Status(error = "${cause.javaClass.simpleName}: ${cause.message.orEmpty().take(180)}")
                                if (statuses[profile] != next) Log.e("PlaybackPriority", "Profile=$profile protection not confirmed", cause)
                                statuses[profile] = next
                            }
                        }
                    } finally {
                        synchronized(this@PriorityAudioRouteGate) {
                            if (token == pending[key]?.token) pending.remove(key)
                        }
                        adapter.closeProfileProxy(profile, proxy)
                    }
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, profileId) } catch (error: Exception) {
                statuses[profileId] = Status(error = "${error.javaClass.simpleName}: ${error.message.orEmpty().take(180)}")
                pending.remove(key)
                continue
            }
            if (!bound) {
                pending.remove(key)
                statuses[profileId] = Status(error = "Unable to bind Bluetooth profile")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized fun restore() {
        desired.clear()
        generations.clear()
        pending.clear()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val addresses = prefs.all.keys.mapNotNull { key ->
            key.substringBeforeLast('_').takeIf { BluetoothManagerAddress.valid(it) }
        }.distinct()
        for (address in addresses) update(adapter.getRemoteDevice(address), true)
    }
}

private object BluetoothManagerAddress {
    fun valid(value: String): Boolean = value.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}"))
}
