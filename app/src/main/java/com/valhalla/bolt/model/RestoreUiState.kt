package com.valhalla.bolt.model

import java.io.File

// This enum is specific to the Restore screen's state machine
enum class RestoreState {
    IDLE,
    CHECKING_ROOT,
    LISTING,
    RESTORING,
    SUCCESS,
    ERROR
}

/**
 * Represents the complete UI state for the Restore screen.
 */
data class RestoreUiState(
    val isRootAvailable: Boolean = false,
    val restoreState: RestoreState = RestoreState.IDLE,
    val backupSessions: List<BackupSession> = emptyList(),
    val processingSteps: List<ProcessingStep> = emptyList(),
    val errorMessage: String? = null,
    val activeSlotSuffix: String? = null
)

data class BackupSession(
    val sessionName: String, // e.g., "Backup_2025-07-13_20-05-30"
    val files: List<BackupFile>
)

enum class RestoreMode {
    ACTIVE_SLOT_ONLY,
    BOTH_SLOTS
}