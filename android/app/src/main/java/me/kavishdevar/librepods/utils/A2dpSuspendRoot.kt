package me.kavishdevar.librepods.utils

import android.os.IBinder
import androidx.annotation.Keep
import me.kavishdevar.librepods.services.A2dpSuspendSnapshot

/** One-shot restoration only. Test 9's interactive HOLD protocol is unsupported. */
@Keep
object A2dpSuspendRoot {
    @JvmStatic fun main(args: Array<String>) {
        try {
            check(args.size == 2 && args[0] == "--restore-only" && args[1] in listOf("true", "false")) {
                "Only saved-state restoration is supported"
            }
            check(android.os.Process.myUid() == 0) { "Root not granted" }
            val vm = Class.forName("dalvik.system.VMRuntime")
            vm.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                .invoke(vm.getDeclaredMethod("getRuntime").invoke(null), arrayOf("L"))
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "audio") as IBinder
            val service = Class.forName("android.media.IAudioService\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            Class.forName("android.media.IAudioService")
                .getMethod("setA2dpSuspended", Boolean::class.javaPrimitiveType)
                .invoke(service, args[1].toBoolean())
            val dump = ProcessBuilder("/system/bin/dumpsys", "-t", "2", "audio").redirectErrorStream(true).start()
            val text = dump.inputStream.bufferedReader().use { it.readText() }
            check(dump.waitFor() == 0) { "Cannot read audio service state" }
            val state = checkNotNull(A2dpSuspendSnapshot.parse(text)) { "Unrecognized audio service state" }
            println("RESTORED ${state.external} ${state.internal} ${state.applied}")
        } catch (error: Throwable) {
            val cause = error.cause ?: error
            println("ERROR ${cause.javaClass.simpleName}: ${cause.message.orEmpty().replace('\n', ' ').take(180)}")
        }
    }
}
