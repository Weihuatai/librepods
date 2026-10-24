package me.kavishdevar.librepods.utils

import android.content.Context
import java.util.concurrent.TimeUnit
import me.kavishdevar.librepods.R

/** Only runs on a deliberate button tap. Script contents are fixed and read-only. */
internal object RootEnvironmentCheck {
    fun run(context: Context): String {
        val script = context.resources.openRawResource(R.raw.librepods_environment_check)
            .bufferedReader().use { it.readText() }
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            process.outputStream.close()
            // The script produces a small, bounded report, well below pipe capacity.
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                context.getString(R.string.priority_root_check_timeout)
            } else {
                process.inputStream.bufferedReader().use { it.readText().take(16_000) }
                    .ifBlank { "Root check returned no output (exit ${process.exitValue()})" }
            }
        } catch (error: Exception) {
            "Root check unavailable: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(200)}"
        } finally {
            process?.destroy()
        }
    }
}
