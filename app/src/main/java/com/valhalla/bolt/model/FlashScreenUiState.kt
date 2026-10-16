package com.valhalla.bolt.model

import android.net.Uri

// Flashing process states
enum class FlashingState {
    IDLE,
    PICKING_ZIP,
    COPYING_ZIP,
    VALIDATING_ZIP,
    EXTRACTING,
    FLASHING,
    SUCCESS,            // Flashing completed but no reboot needed?
    SUCCESS_REBOOT_NEEDED, // New state for when reboot is required
    ERROR,
    REBOOTING
}

// Main UI state holder
data class FlashScreenUiState(
    val isZipValidating: Boolean = false, // Zip validating
    val isValidZip: Boolean = true,      // Whether the picked zip is valid
    val isRootAvailable: Boolean = false,    // Root access status
    val isRootCheckComplete: Boolean = false, // Whether root check has finished
    val flashingState: FlashingState = FlashingState.IDLE,
    val pickedZipUri: Uri? = null,            // Original picked URI
    val copiedZipPath: String? = null,        // Local path after copying
    val fileName: String = "",
    val sha256: String = "",
    val reviewText: String? = null,
    val logPath: String? = null,
    val flashOutput: String = "",             // Console-like output
    val errorMessage: String? = null,         // Error message if any
    val flashedFiles: List<FlashedFile> = emptyList(), // History of flashed files,
    val processingSteps: List<ProcessingStep> = emptyList(),
    val recoveryTargets: List<String> = emptyList(),
    val activeSlot: String? = null,
    val kernelRelease: String? = null,
    val journey: FlashJourney? = null,
)

// History item for flashed kernels
data class FlashedFile(
    val fileName: String,
    val flashDate: String,    // Formatted date string
    val success: Boolean,
    val outputSummary: String,
    val jobId: String? = null,
    val logPath: String? = null,
)

data class ProcessingStep(
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
