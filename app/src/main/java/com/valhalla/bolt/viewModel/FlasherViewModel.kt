package com.valhalla.bolt.viewModel

// FlasherViewModel.kt
import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valhalla.bolt.model.FlashedFile
import com.valhalla.bolt.model.FlashingState
import com.valhalla.bolt.model.HomeUiState
import com.valhalla.bolt.model.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlasherViewModel(application: Application) : AndroidViewModel(application) {

    // UI state exposed to composables
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Shell instance for executing commands
    private lateinit var rootShell: Shell

    // File to store flash history
    private val historyFile by lazy {
        File(getApplication<Application>().filesDir, "flash_history.txt")
    }

    init {
        checkRootAvailability()
        loadFlashHistory()
    }

    fun checkRootAvailability() {
        viewModelScope.launch {
            val isRootAvailable = Shell.isRootAvailable()
            _uiState.update {
                it.copy(
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
        _uiState.update { it.copy(flashingState = FlashingState.PICKING_ZIP) }

        viewModelScope.launch {
            try {
                // Update state to show copying progress
                _uiState.update {
                    it.copy(
                        pickedZipUri = uri,
                        flashingState = FlashingState.COPYING_ZIP
                    )
                }

                // Copy file to app storage
                val copiedPath = copyZipToLocal(uri)

                // Update state with new path
                _uiState.update {
                    it.copy(
                        copiedZipPath = copiedPath,
                        flashingState = FlashingState.IDLE
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        flashingState = FlashingState.ERROR,
                        errorMessage = "Failed to copy file: ${e.message}"
                    )
                }
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

    fun flashKernel(onResult: ((Shell.Result) -> Unit)? = null) {
        val zipPath = _uiState.value.copiedZipPath ?: run {
            _uiState.update {
                it.copy(
                    flashingState = FlashingState.ERROR,
                    errorMessage = "No kernel file selected"
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(flashingState = FlashingState.EXTRACTING) }

                // Create working directory
                val workDir = createWorkDirectory()

                // Extract zip
                extractZip(zipPath, workDir)

                _uiState.update { it.copy(flashingState = FlashingState.FLASHING) }

                // Execute anykernel.sh script
                val result = executeAnykernelScript(workDir)

                if(result is Shell.Result.Error) {
                    throw Exception("Command execution failed: ${result.exception.message}")
                }

                // Process result
                if ((result as Shell.Result.Success).exitCode == 0) {
                    val outputSummary = "Flashing successful at ${getCurrentTime()} - Reboot required"
                    _uiState.update {
                        it.copy(
                            flashingState = FlashingState.SUCCESS_REBOOT_NEEDED, // Use new state
                            flashOutput = result.stdout,
                            flashedFiles = it.flashedFiles + FlashedFile(
                                fileName = File(zipPath).name,
                                flashDate = getCurrentTime(),
                                success = true,
                                outputSummary = outputSummary
                            )
                        )
                    }
                    saveToHistory(File(zipPath).name, outputSummary)
                    onResult?.invoke(result)
                } else {
                    val errorSummary = "Flashing failed: ${result.stderr.take(100)}..."
                    _uiState.update {
                        it.copy(
                            flashingState = FlashingState.ERROR,
                            flashOutput = result.stderr,
                            errorMessage = "Flashing failed (code ${result.exitCode})",
                            flashedFiles = it.flashedFiles + FlashedFile(
                                fileName = File(zipPath).name,
                                flashDate = getCurrentTime(),
                                success = false,
                                outputSummary = errorSummary
                            )
                        )
                    }
                    saveToHistory(File(zipPath).name, errorSummary)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        flashingState = FlashingState.ERROR,
                        errorMessage = "Flashing error: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun createWorkDirectory(): File = withContext(Dispatchers.IO) {
        val workDir = File(getApplication<Application>().cacheDir, "ak3_work")
        workDir.deleteRecursively()
        workDir.mkdirs()
        return@withContext workDir
    }

    private suspend fun extractZip(zipPath: String, destDir: File) {
        // Implement zip extraction using ZipInputStream
        // Omitted for brevity, but would use:
        // ZipInputStream(FileInputStream(zipPath)).use { zip -> ... }
        // Update progress if needed
    }

    fun rebootDevice() {
        viewModelScope.launch {
            _uiState.update { it.copy(flashingState = FlashingState.REBOOTING) }

            val result = rootShell.runCommand("reboot").firstOrNull()

            when (result) {
                is Shell.Result.Success -> {
                    if (result.exitCode == 0) {
                        // Reboot command sent successfully
                        _uiState.update {
                            it.copy(
                                flashingState = FlashingState.REBOOTING,
                                flashOutput = "Device rebooting..."
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                flashingState = FlashingState.ERROR,
                                errorMessage = "Reboot failed with code ${result.exitCode}",
                                flashOutput = result.stderr
                            )
                        }
                    }
                }
                is Shell.Result.Error -> {
                    _uiState.update {
                        it.copy(
                            flashingState = FlashingState.ERROR,
                            errorMessage = "Reboot error: ${result.exception.message}",
                            flashOutput = "Command: ${result.command}\n${result.exception.stackTraceToString()}"
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            flashingState = FlashingState.ERROR,
                            errorMessage = "Unknown reboot error"
                        )
                    }
                }
            }
        }
    }

    private suspend fun executeAnykernelScript(workDir: File): Shell.Result {
        return rootShell.runCommand(
            "cd ${workDir.absolutePath}",
            "chmod +x anykernel.sh",
            "./anykernel.sh"
        ).first() // We only run one command sequence
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

            _uiState.update { it.copy(flashedFiles = history) }
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
                flashingState = FlashingState.IDLE,
                errorMessage = null,
                flashOutput = ""
            )
        }
    }
}