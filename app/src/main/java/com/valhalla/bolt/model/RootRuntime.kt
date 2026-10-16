package com.valhalla.bolt.model

import android.content.Context
import android.os.Process
import org.json.JSONObject
import java.io.File

class RootRuntime(private val context: Context) {
    val busybox: String get() = File(context.applicationInfo.nativeLibraryDir, "libbolt_busybox.so").absolutePath
    suspend fun verify(executor: CommandExecutor) {
        val manifest = context.assets.open("busybox-manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val hashes = manifest.getJSONObject("binarySha256")
        val binary = File(busybox)
        check(binary.isFile && hashes.keys().asSequence().map { hashes.getString(it) }.any { it == ArtifactFiles.sha256(binary) }) { "Bundled BusyBox is missing or its integrity check failed" }
        val applets = executor.execute("${shellQuote(busybox)} --list", 15).requireSuccess("Runtime check").output.lineSequence().toSet()
        check(applets.containsAll(setOf("ash", "unzip", "dd", "flock", "sha256sum", "blockdev"))) { "Bundled runtime lacks required tools" }
    }
    suspend fun ensureIdle(executor: CommandExecutor) {
        locked(executor).execute(":", 15).requireSuccess("Another native Bolt writer may still be running")
    }
    fun locked(executor: CommandExecutor): CommandExecutor = object : CommandExecutor {
        override suspend fun execute(script: String, timeoutSeconds: Long, onOutput: (String) -> Unit): CommandResult {
            // flock is held by the native child even if Android kills the application process.
            val lock = "/data/local/tmp/bolt-${Process.myUid()}.lock"
            return executor.execute("exec ${shellQuote(busybox)} flock -n ${shellQuote(lock)} ${shellQuote(busybox)} ash -c ${shellQuote(script)}", timeoutSeconds, onOutput)
        }
    }
}
