package me.kavishdevar.librepods.utils

import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.Keep
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import me.kavishdevar.librepods.services.A2dpSuspendSnapshot
import me.kavishdevar.librepods.services.A2dpSuspendSession

/** Runs only in a root app_process, never inside system_server. EOF/heartbeat expiry restores state. */
@Keep
object A2dpSuspendRoot {
    @JvmStatic fun main(args: Array<String>) {
        var restore: (() -> Unit)? = null
        val lastCommand = AtomicLong(SystemClock.uptimeMillis())
        val watchdog = Executors.newSingleThreadScheduledExecutor()
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
            fun setSuspend(value: Boolean) { setter.invoke(service, value) }
            fun readSnapshot(): A2dpSuspendSnapshot {
                val dump = ProcessBuilder("/system/bin/dumpsys", "-t", "2", "audio").redirectErrorStream(true).start()
                val text = dump.inputStream.bufferedReader().use { it.readText() }
                check(dump.waitFor() == 0) { "Cannot read audio service state" }
                return checkNotNull(A2dpSuspendSnapshot.parse(text)) { "Unrecognized audio service state" }
            }
            // Recover the baseline without releasing an existing hold on startup.
            // ALLOW (or helper exit) restores it; starting a replacement helper must
            // not briefly admit phone audio while the Mac is still playing.
            val original = args.firstOrNull()?.takeIf { it == "true" || it == "false" }?.toBoolean()
                ?: readSnapshot().external
            val session = A2dpSuspendSession(original, object : A2dpSuspendSession.Access {
                override fun set(suspended: Boolean) = setSuspend(suspended)
                override fun snapshot(): A2dpSuspendSnapshot = readSnapshot()
            })
            restore = { session.close() }
            println("READY $original")
            // Uptime stops during system suspend. Sleeping must not expire a live
            // lease and release phone audio just as the device wakes for playback.
            watchdog.scheduleAtFixedRate({
                if (SystemClock.uptimeMillis() - lastCommand.get() >= 15_000L) {
                    runCatching { restore?.invoke() }
                    kotlin.system.exitProcess(0)
                }
            }, 5, 5, TimeUnit.SECONDS)
            val input = System.`in`.bufferedReader()
            while (true) {
                // Blocking read detects parent death through EOF, without a 100 ms polling loop.
                val command = input.readLine() ?: break
                lastCommand.set(SystemClock.uptimeMillis())
                if (command == "STOP") break
                println(session.command(command))
            }
        } catch (error: Throwable) {
            val cause = error.cause ?: error
            println("ERROR ${cause.javaClass.simpleName}: ${cause.message.orEmpty().replace('\n', ' ').take(180)}")
        } finally {
            watchdog.shutdownNow()
            runCatching { restore?.invoke() }
        }
    }
}
