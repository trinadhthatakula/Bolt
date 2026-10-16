package com.valhalla.bolt.viewModel

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valhalla.bolt.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class RestoreViewModel(
    private val documentHandler: DocumentHandler,
    private val preferenceManager: SharedPreferences,
    private val operations: OperationCoordinator,
    private val runtime: RootRuntime
) : ViewModel() {
    private val mutable = MutableStateFlow(RestoreUiState())
    val uiState = mutable.asStateFlow()
    private val shell = Shell(true)
    private val partitions = PartitionHelper(shell, runtime)
    init {
        checkRootAvailability()
        viewModelScope.launch { operations.status.collect { status ->
            if (status?.kind == "Restore") mutable.update { it.copy(
                restoreState = if (status.running) RestoreState.RESTORING else if (status.phase == "Completed") RestoreState.SUCCESS else RestoreState.ERROR,
                errorMessage = if (!status.running && status.phase != "Completed") status.message else null
            ) }
            if (status != null && !status.running) refresh()
        } }
    }
    fun getBackUpDirectory() = preferenceManager.getString(KEY_BACKUP_URI, null)
    fun saveBackupDirectory(uri: Uri?) { preferenceManager.edit { putString(KEY_BACKUP_URI, uri?.toString()) }; refresh() }
    fun checkRootAvailability() {
        if (operations.isBlocked) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val available = Shell.isRootAvailable()
                mutable.update { it.copy(isRootAvailable = available, activeSlotSuffix = if (available) partitions.getActiveSlotSuffix() else null) }
                refresh()
            } catch (e: Exception) { mutable.update { it.copy(isRootAvailable = false, errorMessage = "Root discovery failed: ${e.message}") } }
        }
    }
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val local = documentHandler.localRecoverySessions()
                val uri = getBackUpDirectory()?.toUri()
                val external = if (uri != null && documentHandler.canAccessUri(uri)) documentHandler.listBackupSessions(uri) else emptyList()
                mutable.update { it.copy(backupSessions = (local + external).sortedByDescending { session -> session.createdAt }) }
            } catch (e: Exception) { mutable.update { it.copy(errorMessage = "Cannot list backups: ${e.message}") } }
        }
    }
    fun listAvailableBackups(backupUri: Uri) { saveBackupDirectory(backupUri) }
    fun restoreSelectedBackup(backup: BackupFile, mode: RestoreMode) {
        val started = operations.launch("Restore") { session ->
            runtime.verify(shell); runtime.ensureIdle(shell)
            val manifest = backup.manifest ?: error("Legacy or incomplete backups cannot be verified; create a new Bolt backup")
            val inventory = partitions.inventory()
            val plan = PartitionPolicy.restorePlan(manifest, backup.partitionName, mode == RestoreMode.BOTH_SLOTS,
                DeviceIdentity.current(documentHandler.context), partitions.getActiveSlotSuffix(), inventory)
            val staged = mutableListOf<File>()
            try {
                // Validate every source and target before the first partition write.
                val sources = plan.map { image ->
                    val uri = backup.sessionImages[image.partition] ?: error("Backup image ${image.partition} is missing")
                    session.phase("Validating recovery", image.partition)
                    val file = documentHandler.stage(uri, image.size).also { staged.add(it) }
                    require(file.length() == image.size && ArtifactFiles.sha256(file) == image.sha256) { "Backup ${image.partition} is truncated or has a digest mismatch" }
                    image to file
                }
                val result = RecoveryWorkflow(partitions).restore(manifest, backup.partitionName, mode == RestoreMode.BOTH_SLOTS,
                    DeviceIdentity.current(documentHandler.context), sources.associate { it.first.partition to it.second }, session::log,
                    beforeWrite = session::mutating, onTarget = session::target)
                check(result.isSuccess) { "Restore was incomplete: ${result.outcomes.joinToString { "${it.partition}: ${it.state} ${it.error.orEmpty()}" }}" }
                session.finish(true, "All ${sources.size} target writes and readback digests were verified. Reboot manually when ready.")
            } finally { staged.forEach { it.parentFile?.deleteRecursively() } }
        }
        if (!started) mutable.update { it.copy(errorMessage = "Another operation is active or has an unknown outcome. Review the operation banner.") }
    }
}
