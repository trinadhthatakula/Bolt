package com.valhalla.bolt.model

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.valhalla.bolt.service.OperationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class OperationStatus(val id: String, val kind: String, val phase: String, val running: Boolean, val message: String, val logPath: String, val tail: String = "", val uncertain: Boolean = false, val outcomes: List<TargetOutcome> = emptyList(), val exitCode: Int? = null)

/** One application-owned operation across all screens, with a durable interrupted state. */
class OperationCoordinator(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = AtomicBoolean(false)
    private var foregroundReady = CompletableDeferred<Unit>()
    private val journal = AtomicFile(File(context.filesDir, "operation.json"))
    private val mutable = MutableStateFlow(load())
    val status = mutable.asStateFlow()
    val isBlocked: Boolean get() = gate.get() || mutable.value?.uncertain == true
    private fun load(): OperationStatus? {
        if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) return null
        return runCatching {
        val json = JSONObject(journal.openRead().bufferedReader().use { it.readText() })
        val outcomes = json.optJSONArray("outcomes")?.let { array -> (0 until array.length()).map { index ->
            array.getJSONObject(index).let { TargetOutcome(it.getString("partition"), it.getString("state"), if (it.isNull("error")) null else it.getString("error")) }
        } } ?: emptyList()
        val logPath = json.getString("logPath")
        val tail = runCatching { java.io.RandomAccessFile(logPath, "r").use { file ->
            file.seek((file.length() - 32768).coerceAtLeast(0)); ByteArray((file.length() - file.filePointer).toInt()).also { file.readFully(it) }.toString(Charsets.UTF_8)
        } }.getOrDefault("")
        val interrupted = json.getBoolean("running") || json.optBoolean("uncertain")
        OperationStatus(json.getString("id"), json.getString("kind"), if (interrupted) "Unknown" else json.getString("phase"), false,
            if (interrupted) "The app stopped during an operation. Its outcome is unknown. Check recovery and logs before continuing." else json.getString("message"), logPath, tail = tail, uncertain = interrupted, outcomes = outcomes, exitCode = if (json.isNull("exitCode")) null else json.getInt("exitCode"))
        }.getOrElse { OperationStatus("unreadable", "Previous operation", "Unknown", false, "The previous operation record cannot be read. Inspect recovery and native processes before continuing.", "", uncertain = true) }
    }
    @Synchronized private fun publish(incoming: OperationStatus, required: Boolean = true) {
        val value = if (mutable.value?.id == incoming.id && mutable.value?.uncertain == true && incoming.running) incoming.copy(uncertain = true, phase = "Unknown") else incoming
        try {
        val json = JSONObject().put("id", value.id).put("kind", value.kind).put("phase", value.phase).put("running", value.running).put("message", value.message).put("logPath", value.logPath).put("uncertain", value.uncertain)
        json.put("exitCode", value.exitCode ?: JSONObject.NULL)
        json.put("outcomes", org.json.JSONArray().apply { value.outcomes.forEach { put(JSONObject().put("partition", it.partition).put("state", it.state).put("error", it.error ?: JSONObject.NULL)) } })
        val stream = journal.startWrite()
        try { stream.write(json.toString().toByteArray()); journal.finishWrite(stream) }
        catch (e: Exception) { journal.failWrite(stream); throw e }
        check(journal.baseFile.isFile && journal.openRead().bufferedReader().use { it.readText() } == json.toString()) { "Operation journal commit verification failed" }
        mutable.value = value
        } catch (e: Exception) {
            mutable.value = value.copy(phase = "Unknown", uncertain = true, message = "Operation record could not be saved: ${e.message}. Inspect logs and recovery before continuing.")
            if (required) throw e
        }
    }
    @Synchronized fun acknowledgeInterruption() {
        if (gate.get()) return
        mutable.value?.takeIf { it.uncertain }?.let { publish(it.copy(uncertain = false, phase = "Acknowledged", message = "Unknown outcome acknowledged; inspect recovery artifacts before any new write."), required = false) }
    }
    @Synchronized fun launch(kind: String, block: suspend (Session) -> Unit): Boolean {
        if (mutable.value?.uncertain == true || !gate.compareAndSet(false, true)) return false
        val id = UUID.randomUUID().toString()
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists() && !logDir.mkdirs()) { gate.set(false); return false }
        val session = Session(OperationStatus(id, kind, "Preparing", true, "Preparing $kind", File(logDir, "$id.log").absolutePath))
        try {
            foregroundReady = CompletableDeferred()
            publish(session.value)
            logDir.listFiles().orEmpty().filter { it.name != "$id.log" }.sortedByDescending { it.lastModified() }.drop(19).forEach { it.delete() }
            ContextCompat.startForegroundService(context, Intent(context, OperationService::class.java))
        } catch (e: Exception) {
            session.finish(false, "Cannot start protected operation: ${e.message}")
            gate.set(false)
            return false
        }
        scope.launch {
            try {
                withTimeout(15000) { foregroundReady.await() }
                block(session)
                if (session.value.running) session.finish(true, "$kind completed")
            } catch (e: Exception) {
                session.log("\nERROR: ${e.message}\n")
                session.finish(false, e.message ?: "Operation failed")
            } finally {
                context.stopService(Intent(context, OperationService::class.java))
                gate.set(false)
            }
        }
        return true
    }
    fun confirmForegroundStarted() { foregroundReady.complete(Unit) }
    fun markTimeout() {
        mutable.value?.let { publish(it.copy(phase = "Unknown", uncertain = true, message = "Android ended foreground execution; check logs and recovery before continuing."), required = false) }
    }
    inner class Session(internal var value: OperationStatus) {
        private var logBytes = 0L
        private var truncated = false
        private var mutationStarted = false
        @Synchronized fun phase(name: String, message: String = name) {
            value = value.copy(phase = name, message = message)
            publish(value)
            log("\n[$name] $message\n")
        }
        @Synchronized fun mutating() { mutationStarted = true; phase("Executing", "A write may be in progress. Keep the operation running.") }
        @Synchronized fun target(outcome: TargetOutcome) {
            value = value.copy(outcomes = value.outcomes.filterNot { it.partition == outcome.partition } + outcome)
            publish(value)
            log("\n${outcome.partition}: ${outcome.state} ${outcome.error.orEmpty()}\n")
        }
        @Synchronized fun exitCode(code: Int?) { value = value.copy(exitCode = code); publish(value); log("\nInstaller exit: $code\n") }
        @Synchronized fun log(text: String) {
            if (logBytes < 8L * 1024 * 1024) {
                try { File(value.logPath).appendText(text); logBytes += text.toByteArray().size }
                catch (_: java.io.IOException) { truncated = true; logBytes = 8L * 1024 * 1024 }
            } else if (!truncated) {
                runCatching { File(value.logPath).appendText("\n[Log retention limit reached; output continues to drain.]\n") }
                truncated = true
            }
            value = value.copy(tail = (value.tail + text).takeLast(32768))
            mutable.update { current -> if (current?.id == value.id) current.copy(tail = value.tail) else current }
        }
        @Synchronized fun finish(success: Boolean, message: String) {
            val phase = if (success) "Completed" else if (mutationStarted) "Partial / failed" else "Failed"
            val previousUncertainty = mutable.value?.uncertain == true
            value = value.copy(phase = if (previousUncertainty) "Unknown" else phase, running = false, message = message, uncertain = previousUncertainty || (!success && mutationStarted))
            publish(value, required = false)
        }
    }
}
