package me.kavishdevar.librepods.services

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray

/** Small diagnostic ring; no addresses, media titles, raw protocol packets or keys. */
internal class PhoneGuardTrace(context: Context) {
    private val prefs = context.getSharedPreferences("phone_guard_diagnostics", Context.MODE_PRIVATE)
    private val events = mutableListOf<String>()
    private var lastDecision = ""
    @Synchronized fun decision(value: String) {
        if (value == lastDecision) return
        lastDecision = value
        record("decision", value)
    }
    @Synchronized fun record(kind: String, value: String) {
        val line = "${System.currentTimeMillis()} / ${SystemClock.elapsedRealtime()} $kind: $value"
        events.add(line)
        while (events.size > 64) events.removeAt(0)
        prefs.edit().putString("last", line).putString("events", JSONArray(events).toString()).apply()
        Log.i("PhoneAudioGuard", "$kind: $value")
    }
}
