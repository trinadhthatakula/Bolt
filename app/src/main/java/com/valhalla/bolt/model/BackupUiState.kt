package com.valhalla.bolt.model

import android.net.Uri
import java.io.File

data class Partition(val name: String)

data class CategorizedPartitions(
    val recommended: List<Partition>,
    val other: List<Partition>
)

data class BackupFile(
    val uri: Uri,
    val fileName: String,
    val partitionName: String
)

enum class BackupRestoreState {
    IDLE,
    CHECKING_ROOT,
    LISTING_PARTITIONS,
    LISTING_BACKUPS,
    BACKING_UP,
    RESTORING,
    SUCCESS,
    ERROR
}

/**
 * Represents the complete UI state for the Backup/Restore screen.
 */
data class BackupUiState(
    val isRootAvailable: Boolean = false,
    val isRootCheckComplete: Boolean = false, // Whether root check has finished
    val backupRestoreState: BackupRestoreState = BackupRestoreState.IDLE,
    val recommendedPartitions: List<Partition> = emptyList(),
    val otherPartitions: List<Partition> = emptyList(),
    val availableBackups: List<BackupFile> = emptyList(),
    val processingSteps: List<ProcessingStep> = emptyList(),
    val errorMessage: String? = null,
    val activeSlotSuffix: String? = null, // Will hold "_a", "_b", or null
    val savedBackupDirectoryUri: String? = null
)