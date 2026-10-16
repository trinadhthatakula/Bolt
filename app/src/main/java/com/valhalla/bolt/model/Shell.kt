package com.valhalla.bolt.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exit 0 is necessary for success; launching a process is not success. */
data class CommandResult(val exitCode: Int?, val output: String, val error: String? = null) {
    val isSuccess: Boolean get() = exitCode == 0 && error == null
    fun requireSuccess(stage: String): CommandResult {
        if (!isSuccess) throw IOException("$stage failed (${exitCode ?: "not started"}): ${error ?: output.takeLast(2000)}")
        return this
    }
}

fun shellQuote(value: String): String {
    require('\u0000' !in value) { "NUL is not a valid shell argument" }
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

interface CommandExecutor {
    suspend fun execute(script: String, timeoutSeconds: Long = 0, onOutput: (String) -> Unit = {}): CommandResult
}

/** One process owns setup/environment and installer. APIs also work on Android 7 (minSdk 24). */
class Shell(private val useSu: Boolean) : CommandExecutor {
    override suspend fun execute(script: String, timeoutSeconds: Long, onOutput: (String) -> Unit): CommandResult = withContext(Dispatchers.IO) {
        val command = if (useSu) arrayOf("su", "-c", "[ \"\$(id -u)\" = 0 ] || exit 126\n$script") else arrayOf("sh", "-c", script)
        val process = try { ProcessBuilder(*command).redirectErrorStream(true).start() }
        catch (e: IOException) { return@withContext CommandResult(null, "", e.message) }
        val tail = StringBuilder()
        val accepting = AtomicBoolean(true)
        val readError = AtomicReference<String?>(null)
        // Drain while waiting, including stderr floods and output without newlines. A daemon
        // cannot pin the caller if a detached descendant keeps the inherited pipe open.
        val drain = Thread({
            try {
                process.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(2048)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (!accepting.get()) break
                        val chunk = String(buffer, 0, count)
                        synchronized(tail) { tail.append(chunk); if (tail.length > 65536) tail.delete(0, tail.length - 65536) }
                        try { onOutput(chunk) } catch (e: Exception) { readError.compareAndSet(null, "Output callback failed: ${e.message}") }
                    }
                }
            } catch (e: IOException) { if (accepting.get()) readError.compareAndSet(null, "Output read failed: ${e.message}") }
        }, "Bolt-shell-output").apply { isDaemon = true; start() }
        try {
            var timedOut = false
            val exit = if (timeoutSeconds <= 0) process.waitFor() else {
                val deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000
                var code: Int? = null
                while (code == null && System.nanoTime() < deadline) {
                    code = runCatching { process.exitValue() }.getOrNull()
                    if (code == null) Thread.sleep(25)
                }
                if (code == null) { timedOut = true; process.destroy() }
                code
            }
            drain.join(2000)
            val openPipe = drain.isAlive
            CommandResult(exit, synchronized(tail) { tail.toString() }, when {
                timedOut -> "Timed out"
                openPipe -> "Output pipe remained open after process exit; a child process may still be running"
                else -> readError.get()
            })
        } finally {
            accepting.set(false)
            if (runCatching { process.exitValue() }.isFailure) process.destroy()
            // Closing a blocked reader can itself block on some platforms; keep it off the caller.
            Thread({ runCatching { process.inputStream.close() } }, "Bolt-shell-close").apply { isDaemon = true; start() }
        }
    }
    companion object {
        suspend fun isRootAvailable(): Boolean = Shell(true).execute("id -u", 15).let { it.isSuccess && it.output.trim() == "0" }
    }
}
