package com.valhalla.bolt.viewModel

// FlasherViewModel.kt
import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valhalla.bolt.model.FlashedFile
import com.valhalla.bolt.model.FlashingState
import com.valhalla.bolt.model.FlashScreenUiState
import com.valhalla.bolt.model.ProcessingStep
import com.valhalla.bolt.model.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

class FlasherViewModel(application: Application) : AndroidViewModel(application) {

    // UI state exposed to composables
    private val _uiState = MutableStateFlow(FlashScreenUiState())
    val uiState: StateFlow<FlashScreenUiState> = _uiState.asStateFlow()

    // Shell instance for executing commands
    private lateinit var rootShell: Shell

    // File to store flash history
    private val historyFile by lazy {
        File(getApplication<Application>().filesDir, "flash_history.txt")
    }

    // New state for reboot confirmation
    private val _showRebootConfirmation = MutableStateFlow(false)
    val showRebootConfirmation: StateFlow<Boolean> = _showRebootConfirmation.asStateFlow()

    init {
        checkRootAvailability()
        loadFlashHistory()
    }

    fun checkRootAvailability() {
        viewModelScope.launch {
            val isRootAvailable = Shell.isRootAvailable()
            _uiState.update {
                it.copy(
                    processingSteps = it.processingSteps + ProcessingStep(
                        title = "Checking Root Access",
                        description = if (isRootAvailable) "Root access is available" else "Root access is not available"
                    ),
                    isRootAvailable = isRootAvailable,
                    isRootCheckComplete = true
                )
            }

            if (isRootAvailable) {
                rootShell = Shell(true)
            }
        }
    }

    fun pickZipFile(uri: Uri) {
        _uiState.update {
            it.copy(
                processingSteps = it.processingSteps + ProcessingStep(
                    title = "Picking Kernel Zip",
                    description = "Selected file: ${uri.path ?: uri.toString()}"
                ),
                pickedZipUri = uri,
                flashingState = FlashingState.PICKING_ZIP
            )
        }

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        processingSteps = it.processingSteps + ProcessingStep(
                            title = "Copying Kernel Zip",
                            description = "Copying selected zip to app storage"
                        ),
                        flashingState = FlashingState.COPYING_ZIP,
                        isZipValidating = true // Show validating state
                    )
                }

                val copiedPath = copyZipToLocal(uri)

                // Set validating state
                _uiState.update { it.copy(
                    processingSteps = it.processingSteps + ProcessingStep(
                        title = "Validating Kernel Zip",
                        description = "Checking if the selected zip is a valid AnyKernel3 zip"
                    ),
                    flashingState = FlashingState.VALIDATING_ZIP
                ) }

                if (!isValidAnyKernelZip(copiedPath)) {
                    _uiState.update {
                        it.copy(
                            processingSteps = it.processingSteps + ProcessingStep(
                                title = "Validation Failed",
                                description = "The selected zip does not contain required AnyKernel3 files"
                            ),
                            flashingState = FlashingState.ERROR,
                            errorMessage = "Invalid AnyKernel3 zip",
                            flashOutput = "Selected file doesn't contain required AnyKernel3 files",
                            isZipValidating = false,
                            isValidZip = false
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        processingSteps = it.processingSteps + listOf(ProcessingStep(
                            title = "Validation Successful",
                            description = "The selected zip is a valid AnyKernel3 zip"
                        )),
                        copiedZipPath = copiedPath,
                        flashingState = FlashingState.IDLE,
                        isZipValidating = false,
                        isValidZip = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        flashingState = FlashingState.ERROR,
                        errorMessage = "Failed to process file: ${e.message}",
                        isZipValidating = false
                    )
                }
            }
        }
    }

    private suspend fun isValidAnyKernelZip(zipPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                ZipFile(zipPath).use { zip ->
                    // Check for root-level anykernel.sh
                    val hasAnyKernelSh = zip.entries().asSequence().any {
                        it.name.equals("anykernel.sh", ignoreCase = true)
                    }

                    // Check for update-binary in META-INF
                    val hasUpdateBinary = zip.entries().asSequence().any {
                        it.name.lowercase().endsWith("meta-inf/com/google/android/update-binary")
                    }

                    // Valid if either exists
                    hasAnyKernelSh || hasUpdateBinary
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun copyZipToLocal(uri: Uri): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver

        val sourceDoc = DocumentFile.fromSingleUri(context, uri)
        val fileName = sourceDoc?.name ?: "kernel_${System.currentTimeMillis()}.zip"
        val destFile = File(context.filesDir, fileName)

        resolver.openInputStream(uri)?.use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Couldn't open input stream for $uri")

        return@withContext destFile.absolutePath
    }

    fun flashKernel() {
        val zipPath = _uiState.value.copiedZipPath ?: run {
            _uiState.update {
                it.copy(
                    processingSteps = it.processingSteps + ProcessingStep(
                        title = "Flashing Error",
                        description = "No kernel zip file selected"
                    ),
                    flashingState = FlashingState.ERROR,
                    errorMessage = "No kernel file selected"
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                // Validate zip before proceeding
                if (!isValidAnyKernelZip(zipPath)) {
                    _uiState.update {
                        it.copy(
                            processingSteps = it.processingSteps + ProcessingStep(
                                title = "Flashing Error",
                                description = "Selected file is not a valid AnyKernel3 zip"
                            ),
                            flashingState = FlashingState.ERROR,
                            errorMessage = "Invalid AnyKernel3 zip file",
                            flashOutput = "Selected file doesn't contain required AnyKernel3 files"
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(flashingState = FlashingState.EXTRACTING) }

                // Create working directory
                val workDir = createWorkDirectory()

                // 1. Export mkbootfs from assets
                exportMkbootfs(workDir)

                // 2. Extract update-binary
                val updateBinary = extractUpdateBinary(zipPath, workDir)

                // 3. Patch update-binary
                patchUpdateBinary(updateBinary, workDir)

                _uiState.update { it.copy(flashingState = FlashingState.FLASHING) }

                // 4. Execute the patched update-binary
                val flashOutput = executeUpdateBinary(updateBinary, zipPath, workDir)

                // 5. Verify success using "done" marker
                if (File(workDir, "done").exists()) {
                    val outputSummary = "Flashing successful - Reboot required"
                    _uiState.update {
                        it.copy(
                            processingSteps = it.processingSteps + ProcessingStep(
                                title = "Flashing Completed",
                                description = outputSummary
                            ),
                            flashingState = FlashingState.SUCCESS_REBOOT_NEEDED,
                            flashOutput = flashOutput,
                            flashedFiles = it.flashedFiles + FlashedFile(
                                fileName = File(zipPath).name,
                                flashDate = getCurrentTime(),
                                success = true,
                                outputSummary = outputSummary
                            )
                        )
                    }
                    saveToHistory(File(zipPath).name, outputSummary)
                } else {
                    throw IOException("Flashing failed - done marker not found")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        processingSteps = it.processingSteps + ProcessingStep(
                            title = "Flashing Error",
                            description = "An error occurred during flashing: ${e.message}"
                        ),
                        flashingState = FlashingState.ERROR,
                        errorMessage = "Flashing error: ${e.message}",
                        flashOutput = e.stackTraceToString()
                    )
                }
            }
        }
    }

    private suspend fun createWorkDirectory(): File = withContext(Dispatchers.IO) {
        _uiState.update {
            it.copy(
                processingSteps = it.processingSteps + ProcessingStep(
                    title = "Creating Work Directory",
                    description = "Preparing working directory for flashing process"
                )
            )
        }
        val workDir = File(getApplication<Application>().cacheDir, "ak3_work")
        workDir.deleteRecursively()
        workDir.mkdirs()
        workDir
    }

    private suspend fun exportMkbootfs(workDir: File) = withContext(Dispatchers.IO) {
        val mkbootfs = File(workDir, "mkbootfs")
        _uiState.update {
            it.copy(
                processingSteps = it.processingSteps + ProcessingStep(
                    title = "Exporting mkbootfs",
                    description = "Extracting mkbootfs binary from assets"
                )
            )
        }
        getApplication<Application>().assets.open("mkbootfs").use { input ->
            mkbootfs.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        // Set executable permissions
        rootShell.runCommand("chmod 755 ${mkbootfs.absolutePath}")
    }

    private suspend fun extractUpdateBinary(zipPath: String, destDir: File): File {
        return withContext(Dispatchers.IO) {
            val updateBinary = File(destDir, "update-binary")
            ZipFile(zipPath).use { zip ->
                val entry = zip.entries().asSequence().find {
                    it.name.endsWith("update-binary")
                } ?: throw IOException("update-binary not found in zip")

                zip.getInputStream(entry).use { input ->
                    updateBinary.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            // Set executable permissions
            rootShell.runCommand("chmod 755 ${updateBinary.absolutePath}")
            updateBinary
        }
    }

    private suspend fun patchUpdateBinary(updateBinary: File, workDir: File) {
        withContext(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    processingSteps = it.processingSteps + ProcessingStep(
                        title = "Patching update-binary",
                        description = "Inserting mkbootfs copy command into update-binary"
                    )
                )
            }
            // Insert mkbootfs copy command before chmod
            val mkbootfsPath = File(workDir, "mkbootfs").absolutePath
            val sedCommand = """
                sed -i '/${"$"}BB chmod -R 755 tools bin;/i cp -f "$mkbootfsPath" ${"$"}AKHOME/tools;' ${updateBinary.absolutePath}
            """.trimIndent()

            rootShell.runCommand(sedCommand)
        }
    }

    private suspend fun executeUpdateBinary(updateBinary: File, zipPath: String, workDir: File): String {
        return withContext(Dispatchers.IO) {
            val commands = listOf(
                "export POSTINSTALL=${workDir.absolutePath}",
                "sh ${updateBinary.absolutePath} 3 1 \"$zipPath\"",
                "touch ${workDir.absolutePath}/done"
            )
            _uiState.update {
                it.copy(
                    processingSteps = it.processingSteps + ProcessingStep(
                        title = "Executing update-binary",
                        description = "Running patched update-binary to flash kernel"
                    )
                )
            }

            val results = rootShell.runCommand(*commands.toTypedArray())

            // Combine all command outputs
            results.joinToString("\n") { result ->
                when (result) {
                    is Shell.Result.Success -> {
                        result.stdout + result.stderr
                    }
                    is Shell.Result.Error -> {
                        "ERROR: ${result.exception.message}"
                    }
                }
            }
        }
    }

    // Reboot functions
    fun confirmReboot() {
        _showRebootConfirmation.value = true
    }

    fun cancelReboot() {
        _showRebootConfirmation.value = false
    }

    fun rebootDevice() {
        viewModelScope.launch {
            _uiState.update { it.copy(flashingState = FlashingState.REBOOTING) }

            val commands = arrayOf(
                "reboot",
                "svc power reboot",
                "busybox reboot",
                "setprop ctl.restart zygote",
                "am restart"
            )

            var rebootSuccess = false
            var lastError = ""

            for (cmd in commands) {
                val result = rootShell.runCommand(cmd).firstOrNull()
                when (result) {
                    is Shell.Result.Success -> {
                        if (result.exitCode == 0) {
                            rebootSuccess = true
                            break
                        } else {
                            lastError = "Exit code ${result.exitCode}: ${result.stderr}"
                        }
                    }
                    is Shell.Result.Error -> {
                        lastError = result.exception.message ?: "Unknown error"
                    }
                    else -> lastError = "Unknown result type"
                }
            }

            if (!rebootSuccess) {
                _uiState.update {
                    it.copy(
                        processingSteps = it.processingSteps + ProcessingStep(
                            title = "Reboot Failed",
                            description = "Failed to reboot device using all methods"
                        ),
                        flashingState = FlashingState.ERROR,
                        errorMessage = "Reboot failed",
                        flashOutput = "Tried multiple methods:\n$lastError"
                    )
                }
            }
        }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun loadFlashHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!historyFile.exists()) return@launch

            val history = mutableListOf<FlashedFile>()
            historyFile.forEachLine { line ->
                val parts = line.split("|")
                if (parts.size == 4) {
                    history.add(
                        FlashedFile(
                            fileName = parts[0],
                            flashDate = parts[1],
                            success = parts[2].toBoolean(),
                            outputSummary = parts[3]
                        )
                    )
                }
            }

            _uiState.update { it.copy(
                processingSteps = it.processingSteps + ProcessingStep(
                    title = "Loading Flash History",
                    description = "Loaded ${history.size} flashed files from history"
                ),
                flashedFiles = history
            ) }
        }
    }

    private fun saveToHistory(fileName: String, summary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = _uiState.value.flashingState == FlashingState.SUCCESS
            val entry = "$fileName|${getCurrentTime()}|$success|$summary\n"
            historyFile.appendText(entry)
        }
    }

    fun resetState() {
        _uiState.update {
            it.copy(
                processingSteps = emptyList(),
                flashingState = FlashingState.IDLE,
                errorMessage = null,
                flashOutput = ""
            )
        }
    }
}