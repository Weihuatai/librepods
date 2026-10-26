package me.kavishdevar.librepods.services

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One bounded cleanup per app process; never observes playback or changes connection policies. */
internal class PhoneSuspendRecovery(private val context: Context) {
    private val prefs = context.getSharedPreferences("phone_a2dp_suspend_recovery", Context.MODE_PRIVATE)
    val status: String get() = processStatus
    companion object {
        private val started = AtomicBoolean(false)
        @Volatile private var processStatus = "Test 9 挂起恢复：未开始"
    }

    fun startOnce() {
        if (!started.compareAndSet(false, true)) return
        if (!prefs.contains("original")) {
            processStatus = "Test 9 挂起恢复：没有保存记录，未修改系统音频状态"
            return
        }
        processStatus = "Test 9 挂起恢复：正在等待旧辅助进程退出（约 16 秒）"
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            try {
                // Old Test 9 helpers restore at heartbeat expiry (15s). Avoid racing them.
                Thread.sleep(16_000L)
                val recovery = SavedA2dpSuspendRecovery(object : SavedA2dpSuspendRecovery.Store {
                    override fun original(): Boolean? = if (prefs.contains("original")) prefs.getBoolean("original", false) else null
                    override fun clear() { check(prefs.edit().remove("original").commit()) { "Cannot clear saved recovery state" } }
                }, SavedA2dpSuspendRecovery.Access { baseline -> restore(baseline) })
                val result = recovery.recover()
                processStatus = if (result == null) "Test 9 挂起恢复：没有待恢复记录" else
                    "Test 9 挂起恢复：已恢复保存值；external=${result.external}, internal=${result.internal}, applied=${result.applied}"
            } catch (error: Exception) {
                processStatus = "Test 9 挂起恢复失败（保留记录，不自动重试）：${error.message.orEmpty().take(180)}"
            } finally { worker.shutdown() }
        }
    }

    private fun restore(baseline: Boolean): A2dpSuspendSnapshot {
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val command = "CLASSPATH=${quote(context.applicationInfo.sourceDir)} /system/bin/app_process /system/bin " +
            "me.kavishdevar.librepods.utils.A2dpSuspendRoot --restore-only $baseline"
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        try {
            check(process.waitFor(12, TimeUnit.SECONDS)) { "Root restoration timed out" }
            val result = process.inputStream.bufferedReader().use { it.readText() }
            val values = Regex("(?m)^RESTORED (true|false) (true|false) (true|false)$").find(result)
                ?: error(result.lineSequence().firstOrNull { it.startsWith("ERROR ") } ?: "Root restoration did not reply")
            return A2dpSuspendSnapshot(values.groupValues[1].toBoolean(), values.groupValues[2].toBoolean(),
                values.groupValues[3].toBoolean())
        } finally {
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            if (process.isAlive) process.destroy()
        }
    }
}
