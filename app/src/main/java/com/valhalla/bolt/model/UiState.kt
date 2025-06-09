package com.valhalla.bolt.model

import android.net.Uri

// Flashing process states
enum class FlashingState {
    IDLE,
    PICKING_ZIP,
    COPYING_ZIP,
    EXTRACTING,
    FLASHING,
    SUCCESS,            // Flashing completed but no reboot needed?
    SUCCESS_REBOOT_NEEDED, // New state for when reboot is required
    ERROR,
    REBOOTING
}

// Main UI state holder
data class HomeUiState(
    val isRootAvailable: Boolean = false,    // Root access status
    val isRootCheckComplete: Boolean = false, // Whether root check has finished
    val flashingState: FlashingState = FlashingState.IDLE,
    val pickedZipUri: Uri? = null,            // Original picked URI
    val copiedZipPath: String? = null,        // Local path after copying
    val flashOutput: String = "",             // Console-like output
    val errorMessage: String? = null,         // Error message if any
    val flashedFiles: List<FlashedFile> = emptyList() // History of flashed files
)

// History item for flashed kernels
data class FlashedFile(
    val fileName: String,
    val flashDate: String,    // Formatted date string
    val success: Boolean,
    val outputSummary: String // Short summary of flash output
)