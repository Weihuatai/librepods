package me.kavishdevar.librepods.services

import android.content.Context
import android.os.SystemClock
import android.os.PowerManager
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Owns only the external suspend bit. Does not disconnect profiles, change volume or route to speaker. */
internal class PhoneA2dpTransport(private val context: Context, private val changed: () -> Unit,
                                  private val report: (String) -> Unit = {}) {
    private val prefs = context.getSharedPreferences("phone_a2dp_suspend_recovery", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val queued = AtomicBoolean(false)
    private val revision = AtomicLong()
    private val forcePending = AtomicBoolean(false)
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LibrePods:GuardCommand").apply { setReferenceCounted(false) }
    private var lastCommandAt = 0L
    @Volatile private var desired = false
    @Volatile private var closed = false
    private var process: Process? = null
    private var input: BufferedReader? = null
    private var output: BufferedWriter? = null
    private var original = false
    @Volatile var snapshot: A2dpSuspendSnapshot? = null; private set
    @Volatile var error: String? = null; private set
    @Volatile private var verifiedAt = 0L
    @Volatile private var retryAfter = 0L
    private var lastReport = ""

    fun holding(): Boolean = desired && fresh() && snapshot?.let { it.external && it.applied } == true
    fun released(): Boolean = !desired && fresh() && snapshot?.let { !it.external && !it.applied } == true
    fun canPlay(): Boolean = !desired && !prefs.contains("original") && error == null
    fun needsRecovery(): Boolean = prefs.contains("original")
    fun requestingHold(): Boolean = desired
    private fun fresh() = SystemClock.elapsedRealtime() - verifiedAt < PhoneTransportSchedule.VERIFY_MS + PhoneTransportSchedule.HEARTBEAT_MS

    private fun reply(): String {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (input?.ready() == true) {
                val line = input?.readLine() ?: error("Root helper exited")
                if (line in setOf("PONG", "HELD") || line.startsWith("READY ") || line.startsWith("STATE ") || line.startsWith("ERROR ")) return line
            } else {
                if (process?.isAlive != true) error("Root helper exited without a reply")
                Thread.sleep(30)
            }
        }
        error("Root helper timed out")
    }

    private fun start() {
        if (process?.isAlive == true) return
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val recovery = if (prefs.contains("original")) prefs.getBoolean("original", false).toString() else ""
        val command = "CLASSPATH=${quote(context.applicationInfo.sourceDir)} /system/bin/app_process /system/bin " +
            "me.kavishdevar.librepods.utils.A2dpSuspendRoot $recovery"
        process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        input = process!!.inputStream.bufferedReader()
        output = process!!.outputStream.bufferedWriter()
        val ready = reply()
        check(ready == "READY true" || ready == "READY false") { ready }
        original = ready.endsWith("true")
    }

    fun request(hold: Boolean, force: Boolean = false) {
        if (closed) return
        desired = hold
        if (force) forcePending.set(true)
        revision.incrementAndGet()
        enqueue()
    }

    private fun enqueue() {
        if (closed || !queued.compareAndSet(false, true)) return
        worker.execute {
            var handled = revision.get()
            var notify = false
            try {
                do {
                    handled = revision.get()
                    val sent = desired
                    if (sent && SystemClock.elapsedRealtime() < retryAfter) break
                    val command = PhoneTransportSchedule.command(sent, prefs.contains("original"),
                        snapshot?.external.takeIf { process?.isAlive == true }, SystemClock.elapsedRealtime(),
                        lastCommandAt, verifiedAt, forcePending.getAndSet(false))
                    if (command == null) {
                        if (!sent && !prefs.contains("original")) { notify = notify || error != null; error = null }
                        break
                    }
                    wakeLock.acquire(PhoneTransportSchedule.COMMAND_WAKE_MS)
                    start()
                    if (sent && !prefs.contains("original")) {
                        check(prefs.edit().putBoolean("original", original).commit()) { "Cannot save suspend baseline" }
                    }
                    output!!.write("$command\n"); output!!.flush()
                    val line = reply()
                    lastCommandAt = SystemClock.elapsedRealtime()
                    if (command == "PING" || command == "REASSERT") {
                        check(line == if (command == "PING") "PONG" else "HELD") { line }
                    } else {
                        val values = Regex("STATE (true|false) (true|false) (true|false)").matchEntire(line)
                        checkNotNull(values) { line }
                        snapshot = A2dpSuspendSnapshot(values.groupValues[1].toBoolean(), values.groupValues[2].toBoolean(),
                            values.groupValues[3].toBoolean())
                        verifiedAt = SystemClock.elapsedRealtime()
                        check(snapshot!!.external == if (sent) true else original) { "Suspend request was not applied" }
                        val message = "requested=$sent external=${snapshot!!.external} internal=${snapshot!!.internal} applied=${snapshot!!.applied}"
                        if (lastReport != message) { lastReport = message; report(message) }
                        notify = true
                        if (!sent) {
                            check(prefs.edit().remove("original").commit()) { "Cannot clear recovered suspend baseline" }
                            stop()
                            snapshot = null
                        }
                    }
                    error = null
                    retryAfter = 0L
                    if (wakeLock.isHeld) wakeLock.release()
                } while (!closed && revision.get() != handled)
            } catch (failure: Exception) {
                error = "${failure.javaClass.simpleName}: ${failure.message.orEmpty().take(200)}"
                if (lastReport != error) { lastReport = error!!; report("error=$error") }
                notify = true
                retryAfter = SystemClock.elapsedRealtime() + 15_000L
                snapshot = null
                stop()
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                queued.set(false)
                if (notify) context.mainExecutor.execute(changed)
                // A release arriving during HOLD must run immediately, not wait for the next audit.
                if (!closed && revision.get() != handled) enqueue()
            }
        }
    }

    private fun stop() {
        val stopping = process
        runCatching { output?.write("STOP\n"); output?.flush() }
        runCatching { output?.close() }
        runCatching { stopping?.waitFor(3_000L, java.util.concurrent.TimeUnit.MILLISECONDS) }
        runCatching { input?.close() }
        process = null; output = null; input = null
        // Closing stdin also releases a surviving root child via its heartbeat deadline.
    }

    fun close() {
        closed = true; desired = false
        worker.execute {
            runCatching {
                if (process != null || prefs.contains("original")) {
                    wakeLock.acquire(PhoneTransportSchedule.COMMAND_WAKE_MS)
                    start(); output!!.write("ALLOW\n"); output!!.flush()
                    val restored = reply()
                    if (restored.startsWith("STATE $original ")) prefs.edit().remove("original").commit()
                }
            }
            stop()
            if (wakeLock.isHeld) wakeLock.release()
        }
        worker.shutdown()
    }
}
