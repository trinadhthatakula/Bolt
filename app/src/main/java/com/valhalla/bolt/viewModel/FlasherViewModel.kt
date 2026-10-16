package com.valhalla.bolt.viewModel

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valhalla.bolt.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class FlasherViewModel(
    application: Application,
    private val documents: DocumentHandler,
    private val operations: OperationCoordinator,
    private val runtime: RootRuntime,
    private val journeys: FlashJourneyStore,
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(FlashScreenUiState())
    val uiState = mutable.asStateFlow()
    private val picking = AtomicBoolean(false)
    private val preparing = AtomicBoolean(false)
    private val shell = Shell(true)
    private val partitions = PartitionHelper(shell, runtime)
    private val history = AtomicFile(File(application.filesDir, "flash-history.json"))
    private val rebootConfirmation = MutableStateFlow(false)
    val showRebootConfirmation = rebootConfirmation.asStateFlow()
    @Volatile private var dismissedOperationId: String? = null
    init {
        checkRootAvailability()
        viewModelScope.launch(Dispatchers.IO) {
            mutable.update { it.copy(flashedFiles = readHistory()) }
        }
        viewModelScope.launch { combine(operations.status, journeys.state) { status, records -> status to records }.collect { (status, records) ->
            if (status?.kind == "Flash" && status.id != dismissedOperationId) publishOperation(status, records)
        } }
        refreshBootStatus()
    }
    private fun publishOperation(status: OperationStatus, records: List<FlashJourney>) {
        val journey = records.firstOrNull { it.id == status.id }
        mutable.update { it.copy(
            journey = journey,
            flashingState = if (status.running) FlashingState.FLASHING else if (status.phase == "Completed") {
                if (journey?.bootStatus in setOf(BootStatus.REBOOT_OBSERVED, BootStatus.USER_CONFIRMED, BootStatus.ISSUE_REPORTED)) FlashingState.SUCCESS else FlashingState.SUCCESS_REBOOT_NEEDED
            } else FlashingState.ERROR,
            flashOutput = status.tail, logPath = status.logPath,
            errorMessage = if (!status.running && status.phase != "Completed") status.message else null
        ) }
    }
    fun showCurrentInstall() {
        val status = operations.status.value?.takeIf { it.kind == "Flash" } ?: return
        dismissedOperationId = null
        publishOperation(status, journeys.state.value)
    }
    fun checkRootAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = Shell.isRootAvailable()
            val kernel = if (available) shell.execute("uname -r").takeIf { it.isSuccess }?.output?.trim() else null
            val slot = if (available && !operations.isBlocked) runCatching { partitions.getActiveSlotSuffix() }.getOrNull() else null
            mutable.update { it.copy(isRootAvailable = available, isRootCheckComplete = true, kernelRelease = kernel, activeSlot = slot) }
        }
    }
    fun pickZipFile(uri: Uri) {
        if (operations.isBlocked || preparing.get() || !picking.compareAndSet(false, true)) { fail("Another operation is active; wait before importing"); return }
        mutable.update { it.copy(flashingState = FlashingState.COPYING_ZIP, isZipValidating = true, reviewText = null, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            var staged: File? = null
            try {
                staged = documents.stage(uri)
                ArtifactFiles.validateAnyKernel(staged)
                val digest = ArtifactFiles.sha256(staged)
                val previous = mutable.value.copiedZipPath
                mutable.update { it.copy(pickedZipUri = uri, copiedZipPath = staged.absolutePath, fileName = documents.displayName(uri), sha256 = digest,
                    flashingState = FlashingState.IDLE, isZipValidating = false, isValidZip = true, errorMessage = null, journey = null) }
                dismissedOperationId = operations.status.value?.id
                if (previous != null && previous != staged.absolutePath) File(previous).parentFile?.deleteRecursively()
            } catch (e: Exception) { staged?.parentFile?.deleteRecursively(); fail(e.message ?: "ZIP import failed") }
            finally { picking.set(false); mutable.update { it.copy(isZipValidating = false) } }
        }
    }
    private fun batteryCheck() {
        val battery = documents.context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: error("Cannot determine battery state")
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        require(plugged || (scale > 0 && level * 100L / scale >= 20)) { "Charge to at least 20% or connect power before flashing" }
    }
    fun prepareFlash() {
        if (operations.isBlocked || picking.get() || !preparing.compareAndSet(false, true)) { fail("An operation is already active or has an unknown outcome"); return }
        val path = mutable.value.copiedZipPath ?: run { preparing.set(false); return }
        mutable.update { it.copy(flashingState = FlashingState.VALIDATING_ZIP) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runtime.verify(shell); runtime.ensureIdle(shell); batteryCheck()
                val zip = File(path)
                require(ArtifactFiles.sha256(zip) == mutable.value.sha256) { "Staged ZIP changed; select it again" }
                val expanded = ArtifactFiles.validateAnyKernel(zip)
                val targets = partitions.recoveryTargets()
                require(documents.context.filesDir.usableSpace > targets.sumOf { it.sizeBytes } + expanded + zip.length() + 64L * 1024 * 1024) { "Insufficient space for recovery and ZIP extraction" }
                mutable.update { it.copy(flashingState = FlashingState.IDLE, reviewText =
                    "Use a kernel package compatible with your device. Its installer chooses which partitions to change; the automatic snapshot covers the original images listed here and cannot guarantee every package can be rolled back.", recoveryTargets = targets.map { target -> target.name }) }
            } catch (e: Exception) { fail(e.message ?: "Preflight failed") }
            finally { preparing.set(false) }
        }
    }
    fun dismissReview() { mutable.update { it.copy(reviewText = null) } }
    fun flashKernel() {
        val selection = mutable.value
        val source = selection.copiedZipPath?.let(::File) ?: return
        if (selection.reviewText == null) { fail("Review the preflight before flashing"); return }
        dismissReview()
        val started = operations.launch("Flash") { session ->
            runtime.verify(shell); runtime.ensureIdle(shell); batteryCheck()
            journeys.begin(session.value.id, selection.fileName, DeviceIdentity.current(documents.context).id, currentBootId())
            require(ArtifactFiles.sha256(source) == selection.sha256) { "Staged ZIP changed; re-import it" }
            val workspace = ArtifactFiles.workspace(File(documents.context.cacheDir, "flash"))
            val zip = File(workspace, "kernel.zip")
            try {
                source.inputStream().use { input -> zip.outputStream().use { ArtifactFiles.copyBounded(input, it, ArtifactFiles.MAX_ZIP_BYTES) } }
                require(ArtifactFiles.sha256(zip) == selection.sha256) { "ZIP changed while staging" }
                val expanded = ArtifactFiles.validateAnyKernel(zip)
                val targets = partitions.recoveryTargets()
                require(documents.context.filesDir.usableSpace > targets.sumOf { it.sizeBytes } + expanded + 64L * 1024 * 1024) { "Insufficient space for recovery and ZIP extraction" }
                val recovery = File(documents.context.filesDir, "recovery/${session.value.id}").apply { check(mkdirs()) }
                require(recovery.usableSpace > targets.sumOf { it.sizeBytes } + 64L * 1024 * 1024) { "Insufficient recovery storage" }
                val result = KernelFlashWorkflow(partitions, AnyKernelInstaller(runtime.locked(shell), runtime.busybox)).flash(
                    zip, selection.sha256, workspace, recovery, targets, session.value.id, DeviceIdentity.current(documents.context),
                    commit = { manifest ->
                        val manifestFile = AtomicFile(File(recovery, "manifest.json"))
                        val output = manifestFile.startWrite()
                        try { output.write(manifest.encode().toByteArray()); manifestFile.finishWrite(output) }
                        catch (e: Exception) { manifestFile.failWrite(output); throw e }
                        require(RecoveryManifest.decode(manifestFile.openRead().bufferedReader().use { it.readText() }) == manifest) { "Recovery manifest verification failed" }
                        session.log("Recovery committed at ${recovery.absolutePath}\nZIP SHA-256: ${selection.sha256}\n")
                    }, onOutput = session::log, beforeInstall = session::mutating, onTarget = session::target
                )
                session.exitCode(result.exitCode)
                val summary = if (result.isSuccess) "Installer completed · exit 0" else "Installer failed (${result.exitCode ?: "not started"}); partition changes may be partial."
                saveHistory(selection.fileName, result.isSuccess, summary, session.value.id, session.value.logPath)
                result.requireSuccess("AnyKernel installer")
                session.finish(true, summary)
                if (operations.status.value?.phase == "Completed") journeys.complete(session.value.id)
            } finally {
                // Root-created AKHOME files may not be removable by the app UID.
                val cleaned = shell.execute("rm -rf -- ${shellQuote(workspace.absolutePath)}")
                if (!cleaned.isSuccess) session.log("\nCleanup warning: ${cleaned.output}\n")
                source.parentFile?.deleteRecursively()
                mutable.update { it.copy(copiedZipPath = null) }
            }
        }
        if (!started) fail("Another operation is active or has an unknown outcome")
    }
    fun resetState() {
        if (operations.isBlocked || picking.get() || preparing.get()) return
        mutable.value.copiedZipPath?.let { File(it).parentFile?.deleteRecursively() }
        dismissedOperationId = operations.status.value?.id
        mutable.update { it.copy(flashingState = FlashingState.IDLE, pickedZipUri = null, copiedZipPath = null, fileName = "", sha256 = "", reviewText = null, isValidZip = true, errorMessage = null, journey = null, logPath = null) }
    }
    private fun fail(message: String) { mutable.update { it.copy(flashingState = FlashingState.ERROR, errorMessage = message, reviewText = null) } }
    fun confirmReboot() { if (!operations.isBlocked && mutable.value.flashingState == FlashingState.SUCCESS_REBOOT_NEEDED) rebootConfirmation.value = true }
    fun cancelReboot() { rebootConfirmation.value = false }
    fun rebootDevice() {
        if (operations.isBlocked) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = shell.execute("reboot", 15)
            if (!result.isSuccess) mutable.update { it.copy(errorMessage = "Reboot command failed. Reboot manually. ${result.output}") }
        }
    }
    private suspend fun currentBootId(): String? = shell.execute("cat /proc/sys/kernel/random/boot_id").takeIf { it.isSuccess }?.output?.trim()?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
    fun refreshBootStatus() {
        if (operations.isBlocked) return
        viewModelScope.launch(Dispatchers.IO) { runCatching {
            val device = DeviceIdentity.current(documents.context).id
            val status = operations.status.value
            if (status?.kind == "Flash" && status.phase == "Completed" && journeys.state.value.none { it.id == status.id })
                journeys.addLegacy(status.id, readHistory().firstOrNull()?.fileName ?: "Previous kernel", device)
            journeys.observeLatest(device, currentBootId())
        } }
    }
    fun confirmKernelWorking(working: Boolean) {
        val id = mutable.value.journey?.id ?: return
        viewModelScope.launch(Dispatchers.IO) { journeys.feedback(id, working) }
    }
    private fun readHistory(): List<FlashedFile> = runCatching {
        val array = JSONArray(history.openRead().bufferedReader().use { it.readText() })
            (0 until array.length()).map { index -> array.getJSONObject(index).let { FlashedFile(it.getString("file"), it.getString("date"), it.getBoolean("success"), it.getString("summary"), it.optString("id").takeIf { id -> id.isNotEmpty() }, it.optString("log").takeIf { path -> path.isNotEmpty() }) } }
    }.getOrElse {
        File(documents.context.filesDir, "flash_history.txt").takeIf { it.exists() }?.readLines()?.mapNotNull { line ->
            line.split('|').takeIf { it.size == 4 }?.let { FlashedFile(it[0], it[1], false, "Legacy result (unverified): ${it[3]}") }
        } ?: emptyList()
    }
    private fun saveHistory(name: String, success: Boolean, summary: String, id: String, log: String) {
        val item = FlashedFile(name, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()), success, summary, id, log)
        val records = (listOf(item) + readHistory()).take(50)
        val array = JSONArray().apply { records.forEach { put(JSONObject().put("file", it.fileName).put("date", it.flashDate).put("success", it.success).put("summary", it.outputSummary).put("id", it.jobId ?: "").put("log", it.logPath ?: "")) } }
        val output = history.startWrite()
        try { output.write(array.toString().toByteArray()); history.finishWrite(output) }
        catch (e: Exception) { history.failWrite(output); throw e }
        require(history.baseFile.isFile && history.openRead().bufferedReader().use { it.readText() } == array.toString()) { "Result history could not be committed" }
        mutable.update { it.copy(flashedFiles = records) }
    }
}
