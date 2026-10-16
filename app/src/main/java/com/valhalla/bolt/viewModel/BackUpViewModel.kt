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
import java.util.UUID

class BackUpViewModel(
    private val documentHandler: DocumentHandler,
    private val preferenceManager: SharedPreferences,
    private val operations: OperationCoordinator,
    private val runtime: RootRuntime
) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val uiState = mutable.asStateFlow()
    private val checking = java.util.concurrent.atomic.AtomicBoolean(false)
    private val shell = Shell(true)
    private val partitions = PartitionHelper(shell, runtime)
    init {
        checkRootAvailability()
        viewModelScope.launch { operations.status.collect { status ->
            if (status?.running != true && !operations.isBlocked && !mutable.value.isRootCheckComplete) checkRootAvailability()
            if (status?.kind == "Backup") mutable.update { it.copy(
                backupRestoreState = if (status.running) BackupRestoreState.BACKING_UP else if (status.phase == "Completed") BackupRestoreState.SUCCESS else BackupRestoreState.ERROR,
                errorMessage = if (!status.running && status.phase != "Completed") status.message else null
            ) }
        } }
    }
    private fun step(title: String, message: String) { mutable.update { it.copy(processingSteps = (it.processingSteps + ProcessingStep(title, message)).takeLast(200)) } }
    fun checkRootAvailability() {
        if (operations.isBlocked) {
            mutable.update { it.copy(backupRestoreState = BackupRestoreState.ERROR, errorMessage = "Root discovery waits until the active or unknown operation is resolved.") }
            return
        }
        if (!checking.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mutable.update { it.copy(backupRestoreState = BackupRestoreState.CHECKING_ROOT) }
                check(Shell.isRootAvailable()) { "Root access is required to create partition backups" }
                runtime.verify(shell); runtime.ensureIdle(shell)
                val found = partitions.getCategorizedPartitions()
                mutable.update { it.copy(isRootAvailable = true, isRootCheckComplete = true,
                    backupRestoreState = BackupRestoreState.IDLE, recommendedPartitions = found.recommended,
                    otherPartitions = found.other, activeSlotSuffix = partitions.getActiveSlotSuffix(),
                    savedBackupDirectoryUri = getBackUpDirectory()?.takeIf { uri -> documentHandler.canAccessUri(uri.toUri()) }, errorMessage = null) }
                step("Discovery", "Found ${found.recommended.size + found.other.size} validated block targets")
            } catch (e: Exception) { mutable.update { it.copy(isRootCheckComplete = true, isRootAvailable = false, backupRestoreState = BackupRestoreState.ERROR, errorMessage = e.message) } }
            finally { checking.set(false) }
        }
    }
    fun getBackUpDirectory() = preferenceManager.getString(KEY_BACKUP_URI, null)
    fun saveBackupDirectory(uri: Uri?) {
        preferenceManager.edit { putString(KEY_BACKUP_URI, uri?.toString()) }
        mutable.update { it.copy(savedBackupDirectoryUri = uri?.toString()) }
    }
    fun saveBackupDirectoryAndStartBackup(partitions: List<Partition>, destinationUri: Uri) { saveBackupDirectory(destinationUri); startBackup(partitions) }
    fun startBackup(selected: List<Partition>) {
        val destination = mutable.value.savedBackupDirectoryUri?.toUri()
        if (destination == null || selected.isEmpty()) { mutable.update { it.copy(errorMessage = "Select partitions and a backup directory") }; return }
        val names = selected.map { it.name }.distinct()
        val started = operations.launch("Backup") { session ->
            runtime.verify(shell); runtime.ensureIdle(shell)
            val inventory = partitions.inventory()
            val targets = names.map { name -> inventory.singleOrNull { it.name == name } ?: error("Target $name is no longer available") }
            val identity = DeviceIdentity.current(documentHandler.context)
            val directory = ArtifactFiles.workspace(File(documentHandler.context.cacheDir, "backups"))
            try {
                require(directory.usableSpace > targets.sumOf { it.sizeBytes } + 64L * 1024 * 1024) { "Insufficient space to stage the backup" }
                val targetDirectory = documentHandler.createBackupSessionDir(destination, "Backup_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}").getOrThrow()
                val images = targets.map { target ->
                    session.target(TargetOutcome(target.name, "Exporting"))
                    session.phase("Backing up", target.name); step("Backing up", target.name)
                    val file = File(directory, "${target.name}.img").apply { check(createNewFile()) }
                    partitions.backup(target, file, session::log)
                    val image = RecoveryImage(target.name, file.length(), ArtifactFiles.sha256(file), PartitionPolicy.suffix(target.name), target.path)
                    val document = targetDirectory.createFile("application/octet-stream", "${target.name}.img") ?: error("Cannot create backup image")
                    documentHandler.writeVerified(document, file)
                    session.target(TargetOutcome(target.name, "Verified"))
                    step("Verified", "${target.name}: copied/exported digest verified")
                    image
                }
                documentHandler.writeManifest(targetDirectory, RecoveryManifest(session.value.id, identity, System.currentTimeMillis(), images))
                session.finish(true, "All ${images.size} backups and the exported manifest were verified")
                step("Completed", "Complete verified backup manifest committed")
            } finally { directory.deleteRecursively() }
        }
        if (!started) mutable.update { it.copy(errorMessage = "Another operation is active or has an unknown outcome. Review the operation banner.") }
    }
}
