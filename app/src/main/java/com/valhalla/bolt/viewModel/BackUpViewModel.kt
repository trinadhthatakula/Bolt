package com.valhalla.bolt.viewModel

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valhalla.bolt.model.BACKUP_DIR_NAME
import com.valhalla.bolt.model.BackupRestoreState
import com.valhalla.bolt.model.BackupUiState
import com.valhalla.bolt.model.DocumentHandler
import com.valhalla.bolt.model.KEY_BACKUP_URI
import com.valhalla.bolt.model.Partition
import com.valhalla.bolt.model.PartitionHelper
import com.valhalla.bolt.model.ProcessingStep
import com.valhalla.bolt.model.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


var backUpListener: (() -> Unit)? = null

class BackUpViewModel(
    private val documentHandler: DocumentHandler,
    private val preferenceManager : SharedPreferences,
): ViewModel(){

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private lateinit var rootShell: Shell
    private lateinit var partitionHelper: PartitionHelper

    init {
        checkRootAvailability()
    }

    private fun addProcessingStep(step: ProcessingStep) {
        _uiState.update { it.copy(processingSteps = it.processingSteps + step) }
    }

    fun checkRootAvailability() {
        viewModelScope.launch {
            _uiState.update { it.copy(backupRestoreState = BackupRestoreState.CHECKING_ROOT) }
            addProcessingStep(ProcessingStep("Root Check", "Checking for root access..."))

            val isRootAvailable = Shell.isRootAvailable()
            _uiState.update { it.copy(isRootAvailable = isRootAvailable) }

            if (isRootAvailable) {
                rootShell = Shell(true)
                partitionHelper = PartitionHelper(rootShell)
                addProcessingStep(ProcessingStep("Root Check", "Root access granted."))
                listPartitionsForBackup() // Automatically list partitions on successful root check
                loadSavedBackupLocation()
            } else {
                addProcessingStep(ProcessingStep("Root Check", "Root access not available. This feature is disabled."))
                _uiState.update {
                    it.copy(
                        backupRestoreState = BackupRestoreState.ERROR,
                        errorMessage = "Root access is required for backup and restore."
                    )
                }
            }
        }
    }

    fun getBackUpDirectory() = preferenceManager.getString(KEY_BACKUP_URI, null)

    private fun loadSavedBackupLocation() {
        val uriString = getBackUpDirectory()
        if (uriString != null ) {
            if(documentHandler.exists(uriString.toUri(), BACKUP_DIR_NAME)) {
                _uiState.update { it.copy(savedBackupDirectoryUri = uriString) }
                addProcessingStep(ProcessingStep("Info", "Saved backup location loaded."))
            } else{
                Log.e("BackUp ViewModel", "loadSavedBackupLocation: Saved backup location does not exist.")
                _uiState.update { it.copy(savedBackupDirectoryUri = null) }
                saveBackupDirectory(null)
                addProcessingStep(ProcessingStep("Error", "Saved backup location does not exist."))
            }
        }
    }

    private fun listPartitionsForBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(backupRestoreState = BackupRestoreState.LISTING_PARTITIONS) }
            addProcessingStep(ProcessingStep("Listing Partitions", "Scanning for available partitions..."))

            // We can get the active slot at the same time we get partitions
            val activeSlot = partitionHelper.getActiveSlotSuffix()// Fetch the active slot
            val categorizedData = partitionHelper.getCategorizedPartitions()

            _uiState.update {
                it.copy(
                    backupRestoreState = BackupRestoreState.IDLE,
                    recommendedPartitions = categorizedData.recommended,
                    otherPartitions = categorizedData.other,
                    activeSlotSuffix = activeSlot // --- POPULATE THE NEW STATE FIELD ---
                )
            }
            val totalFound = categorizedData.recommended.size + categorizedData.other.size
            addProcessingStep(ProcessingStep("Scan Complete", "Found $totalFound relevant partitions."))
        }
    }

    fun saveBackupDirectory(uri: Uri?) {
        viewModelScope.launch {
            preferenceManager.edit{
                putString(KEY_BACKUP_URI, uri?.toString())
            }
        }
    }

    fun saveBackupDirectoryAndStartBackup(partitions: List<Partition>, destinationUri: Uri) {
        saveBackupDirectory(destinationUri)
        _uiState.update { it.copy(savedBackupDirectoryUri = destinationUri.toString()) }
        // Now that the URI is saved in the state, proceed with the backup
        startBackup(partitions)
    }


    fun startBackup(partitions: List<Partition>) {
        val destinationUriString = _uiState.value.savedBackupDirectoryUri
        if (destinationUriString == null) {
            _uiState.update { it.copy(errorMessage = "No backup directory selected.") }
            return
        }
        val destinationUri = destinationUriString.toUri()
        viewModelScope.launch {
            _uiState.update { it.copy(backupRestoreState = BackupRestoreState.BACKING_UP) }
            saveBackupDirectory(destinationUri)
            try {

                // 2. Create a timestamped folder for this specific backup session
                val timestamp = SimpleDateFormat("dd_MMM,_yyyy;_HH-mm-ss", Locale.getDefault()).format(
                    Date()
                )
                val sessionDirName = "Backup_$timestamp"
                documentHandler.createBackupSessionDir(destinationUri, sessionDirName).onSuccess { result ->
                    val sessionDir = result
                    addProcessingStep(ProcessingStep("Creating Session", "Saving backups to folder: $sessionDirName"))

                    for (partition in partitions) {
                        val fileName = "${partition.name}.img"
                        addProcessingStep(ProcessingStep("Backing up ${partition.name}", "Please wait..."))

                        val tempFile = documentHandler.cacheFile(fileName)
                        val result = partitionHelper.backupPartition(partition.name, tempFile.absolutePath)

                        if (result is Shell.Result.Success && result.exitCode == 0) {
                            // 3. Save the backup file inside the new session directory
                            val finalFile = sessionDir.createFile("application/octet-stream", fileName)
                                ?: throw IOException("Could not create file in session directory")
                            documentHandler.writeToFile(
                                finalFile,
                                tempFile
                            )
                            tempFile.delete()
                            addProcessingStep(ProcessingStep("Success: ${partition.name}", "Backed up to $fileName"))
                        } else {
                            val errorMessage = (result as Shell.Result.Error).exception.message
                            _uiState.update {
                                it.copy(errorMessage = errorMessage)
                            }
                            addProcessingStep(ProcessingStep("Backup Failed", "Error: $errorMessage"))
                        }
                    }
                    _uiState.update { it.copy(backupRestoreState = BackupRestoreState.SUCCESS) }
                    addProcessingStep(ProcessingStep("All Done!", "All selected partitions have been backed up."))
                }.onFailure { exception ->
                    val errorMessage = exception.message
                    _uiState.update {
                        it.copy(errorMessage = errorMessage)
                    }
                    addProcessingStep(ProcessingStep("Backup Failed", "Error: $errorMessage"))
                }


            } catch (e: IOException) {
                addProcessingStep(ProcessingStep("Backup Failed", "Error: ${e.message}"))
                _uiState.update { it.copy(backupRestoreState = BackupRestoreState.ERROR, errorMessage = e.message) }
            } finally {
                backUpListener?.invoke()
            }
        }
    }


}