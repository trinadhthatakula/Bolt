package com.valhalla.bolt.viewModel

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valhalla.bolt.model.BACKUP_DIR_NAME
import com.valhalla.bolt.model.BackupFile
import com.valhalla.bolt.model.DocumentHandler
import com.valhalla.bolt.model.KEY_BACKUP_URI
import com.valhalla.bolt.model.PartitionHelper
import com.valhalla.bolt.model.ProcessingStep
import com.valhalla.bolt.model.RestoreMode
import com.valhalla.bolt.model.RestoreState
import com.valhalla.bolt.model.RestoreUiState
import com.valhalla.bolt.model.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class RestoreViewModel(
    private val documentHandler: DocumentHandler,
    private val preferenceManager : SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = _uiState.asStateFlow()

    private lateinit var rootShell: Shell
    private lateinit var partitionHelper: PartitionHelper
    private val backupDirName = "Bolt_Backups"

    init {
        checkRootAvailability()
        backUpListener = {
            checkRootAvailability()
        }
    }

    private fun addProcessingStep(step: ProcessingStep) {
        _uiState.update { it.copy(processingSteps = it.processingSteps + step) }
    }

    fun checkRootAvailability() {
        viewModelScope.launch {
            _uiState.update { it.copy(restoreState = RestoreState.CHECKING_ROOT) }
            addProcessingStep(ProcessingStep("Root Check", "Checking for root access..."))

            if (Shell.isRootAvailable()) {
                rootShell = Shell(true)
                partitionHelper = PartitionHelper(rootShell)
                _uiState.update { it.copy(isRootAvailable = true) }
                addProcessingStep(ProcessingStep("Root Check", "Root access granted."))
                _uiState.update { it.copy(activeSlotSuffix = partitionHelper.getActiveSlotSuffix()) }
                loadBackupsFromSavedLocation()
            } else {
                addProcessingStep(ProcessingStep("Root Check", "Root access not available."))
                _uiState.update {
                    it.copy(
                        restoreState = RestoreState.ERROR,
                        errorMessage = "Root access is required to restore backups."
                    )
                }
            }
        }
    }

    fun getBackUpDirectory() = preferenceManager.getString(KEY_BACKUP_URI, null)

    fun saveBackupDirectory(uri: Uri?) {
        viewModelScope.launch {
            preferenceManager.edit{
                putString(KEY_BACKUP_URI, uri?.toString())
            }
        }
    }

    private fun loadBackupsFromSavedLocation() {
        val uriString = getBackUpDirectory()
        if (uriString == null) {
            addProcessingStep(ProcessingStep("Info", "No backup location saved. Please select a directory."))
            return
        }

        val uri = uriString.toUri()

        // 1. First, check if we have permission AT ALL.
        if (!documentHandler.canAccessUri(uri)) {
            addProcessingStep(ProcessingStep("Error", "Permission for saved backup location lost. Please select it again."))
            saveBackupDirectory(null)
            return
        }

        // 2. Now that we have permission, check if our specific folder exists.
        if (!documentHandler.exists(uri, BACKUP_DIR_NAME)) {
            addProcessingStep(ProcessingStep("Info", "'$BACKUP_DIR_NAME' folder not found. Create a backup first."))
            // We can clear the backup list in the UI state to be safe
            _uiState.update { it.copy(backupSessions = emptyList()) }
            return
        }

        // If both checks pass, we can safely list the backups.
        listAvailableBackups(uri)
    }


    fun listAvailableBackups( backupUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(restoreState = RestoreState.LISTING) }
            addProcessingStep(ProcessingStep("Scanning", "selected directory = $backupUri"))
            addProcessingStep(ProcessingStep("Scanning", "Looking for backups in selected directory..."))

            try {

                val sessions = documentHandler.listBackupSessions(backupUri)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(restoreState = RestoreState.IDLE, backupSessions = sessions) }
                    addProcessingStep(ProcessingStep("Scan Complete", "Found ${sessions.size} backup sessions."))
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    addProcessingStep(ProcessingStep("error", "error scanning for backups: ${e.message}"))
                    _uiState.update { it.copy(restoreState = RestoreState.ERROR, errorMessage = e.message) }
                }
            }
        }
    }

    fun restoreSelectedBackup(backup: BackupFile, mode: RestoreMode) {
        viewModelScope.launch {
            val activeSlot = _uiState.value.activeSlotSuffix

            // Safety Check for A/B devices
            if (activeSlot != null && !backup.partitionName.endsWith(activeSlot)) {
                addProcessingStep(ProcessingStep("Safety Error", "Restore failed: You are trying to restore a backup from an inactive slot (${backup.partitionName}) to an active slot ($activeSlot). This is unsafe."))
                _uiState.update { it.copy(restoreState = RestoreState.ERROR, errorMessage = "Inactive slot backup mismatch.") }
                return@launch
            }

            _uiState.update { it.copy(restoreState = RestoreState.RESTORING) }
            addProcessingStep(ProcessingStep("Preparing", "Copying ${backup.fileName} to cache..."))

            documentHandler.copyToCache(backup.uri).onSuccess { tempFile ->
                // Restore to the primary (active) partition
                addProcessingStep(ProcessingStep("Restoring", "Flashing to ${backup.partitionName}..."))
                val result1 = partitionHelper.restorePartition(tempFile.absolutePath, backup.partitionName)

                if (result1 is Shell.Result.Success && result1.exitCode == 0) {
                    // If successful and user chose "Both Slots", flash to the other slot
                    if (mode == RestoreMode.BOTH_SLOTS && activeSlot != null) {
                        val otherSlot = if (activeSlot == "_a") "_b" else "_a"
                        val baseName = backup.partitionName.removeSuffix(activeSlot)
                        val otherPartitionName = "$baseName$otherSlot"

                        addProcessingStep(ProcessingStep("Restoring (Inactive)", "Flashing to $otherPartitionName..."))
                        partitionHelper.restorePartition(tempFile.absolutePath, otherPartitionName)
                    }

                    _uiState.update { it.copy(restoreState = RestoreState.SUCCESS) }
                    addProcessingStep(ProcessingStep("Success!", "Restore complete. Reboot is recommended."))
                } else {
                    addProcessingStep(ProcessingStep("Error", "Restore failed."))
                }

                tempFile.delete() // Clean up temp file

            }.onFailure { exception ->
                exception.printStackTrace()
            }
        }
    }
}