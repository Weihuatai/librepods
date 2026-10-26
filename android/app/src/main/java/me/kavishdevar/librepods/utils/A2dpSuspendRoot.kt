package me.kavishdevar.librepods.utils

import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.Keep
import me.kavishdevar.librepods.services.A2dpSuspendSnapshot

/** Runs only in a root app_process, never inside system_server. EOF/heartbeat expiry restores state. */
@Keep
object A2dpSuspendRoot {
    @JvmStatic fun main(args: Array<String>) {
        var restore: (() -> Unit)? = null
        try {
            check(android.os.Process.myUid() == 0) { "Root not granted" }
            val vm = Class.forName("dalvik.system.VMRuntime")
            vm.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                .invoke(vm.getDeclaredMethod("getRuntime").invoke(null), arrayOf("L"))
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "audio") as IBinder
            val api = Class.forName("android.media.IAudioService")
            val service = Class.forName("android.media.IAudioService\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val setter = api.getMethod("setA2dpSuspended", Boolean::class.javaPrimitiveType)
            fun set(value: Boolean) { setter.invoke(service, value) }
            fun snapshot(): A2dpSuspendSnapshot {
                val dump = ProcessBuilder("/system/bin/dumpsys", "-t", "2", "audio").redirectErrorStream(true).start()
                val text = dump.inputStream.bufferedReader().use { it.readText() }
                check(dump.waitFor() == 0) { "Cannot read audio service state" }
                return checkNotNull(A2dpSuspendSnapshot.parse(text)) { "Unrecognized audio service state" }
            }
            // Recover the baseline without releasing an existing hold on startup.
            // ALLOW (or helper exit) restores it; starting a replacement helper must
            // not briefly admit phone audio while the Mac is still playing.
            val original = args.firstOrNull()?.takeIf { it == "true" || it == "false" }?.toBoolean()
                ?: snapshot().external
            restore = { set(original) }
            println("READY $original")
            val input = System.`in`.bufferedReader()
            var lastCommand = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - lastCommand < 15_000L) {
                if (!input.ready()) { Thread.sleep(100); continue }
                val command = input.readLine() ?: break
                lastCommand = SystemClock.elapsedRealtime()
                if (command == "STOP") break
                check(command == "HOLD" || command == "ALLOW") { "Invalid command" }
                val desired = if (command == "HOLD") true else original
                val before = snapshot()
                val state = if (before.external != desired || (desired && !before.applied)) {
                    set(desired)
                    snapshot()
                } else before
                println("STATE ${state.external} ${state.internal} ${state.applied}")
            }
        } catch (error: Throwable) {
            val cause = error.cause ?: error
            println("ERROR ${cause.javaClass.simpleName}: ${cause.message.orEmpty().replace('\n', ' ').take(180)}")
        } finally {
            runCatching { restore?.invoke() }
        }
    }
}
