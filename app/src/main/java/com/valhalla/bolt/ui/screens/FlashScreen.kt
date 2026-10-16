package com.valhalla.bolt.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.model.FlashingState
import com.valhalla.bolt.model.OperationCoordinator
import com.valhalla.bolt.viewModel.FlasherViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun FlashScreen(modifier: Modifier = Modifier, viewModel: FlasherViewModel = koinViewModel(), incomingUri: Uri? = null, onImportConsumed: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val operations: OperationCoordinator = koinInject()
    val operation by operations.status.collectAsStateWithLifecycle()
    val reboot by viewModel.showRebootConfirmation.collectAsStateWithLifecycle()
    var logs by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::pickZipFile) }
    val busy = operation?.running == true || operation?.uncertain == true || state.isZipValidating || state.flashingState == FlashingState.VALIDATING_ZIP
    if (incomingUri != null) AlertDialog(
        onDismissRequest = onImportConsumed,
        title = { Text("Import AnyKernel ZIP?") },
        text = { Text("Bolt will copy and validate the selected document. Flashing requires a separate device and recovery review.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.pickZipFile(incomingUri); onImportConsumed() }) { Text("Import ZIP") } },
        dismissButton = { TextButton(onClick = onImportConsumed) { Text("Cancel") } }
    )
    state.reviewText?.let { review -> AlertDialog(
        onDismissRequest = viewModel::dismissReview,
        title = { Text("Review kernel operation") },
        text = { LazyColumn { item { Text(review) } } },
        confirmButton = { TextButton(onClick = viewModel::flashKernel) { Text("Flash ZIP") } },
        dismissButton = { TextButton(onClick = viewModel::dismissReview) { Text("Cancel") } }
    ) }
    if (reboot) AlertDialog(
        onDismissRequest = viewModel::cancelReboot,
        title = { Text("Reboot device?") }, text = { Text("Reboot now to load the installed kernel?") },
        confirmButton = { TextButton(onClick = { viewModel.cancelReboot(); viewModel.rebootDevice() }) { Text("Reboot now") } },
        dismissButton = { TextButton(onClick = viewModel::cancelReboot) { Text("Cancel") } }
    )
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Bolt Kernel Flasher", style = MaterialTheme.typography.headlineSmall) }
        item {
            if (!state.isRootCheckComplete) Text("Checking root access…")
            else if (!state.isRootAvailable) {
                Text("Root access is required to flash. ZIPs can still be imported and validated.")
                TextButton(enabled = !busy, onClick = viewModel::checkRootAvailability) { Text("Retry root access") }
            }
        }
        item {
            Button(enabled = !busy, onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                Text(if (state.copiedZipPath == null) "Select AnyKernel ZIP" else "Change ZIP")
            }
        }
        if (state.isZipValidating || state.flashingState == FlashingState.VALIDATING_ZIP) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Validating document and device…") }
        if (state.copiedZipPath != null) {
            item { Text(state.fileName, style = MaterialTheme.typography.titleMedium); Text("SHA-256: ${state.sha256}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            item { Button(enabled = !busy && state.isRootAvailable, onClick = viewModel::prepareFlash) { Text("Review and flash") } }
        }
        state.errorMessage?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (state.flashingState == FlashingState.SUCCESS_REBOOT_NEEDED) item {
            Text("Installer completed. Reboot and device health are not yet verified.")
            Button(enabled = !busy, onClick = viewModel::confirmReboot) { Text("Reboot") }
        }
        if (state.flashingState == FlashingState.ERROR || state.flashingState == FlashingState.SUCCESS_REBOOT_NEEDED) item {
            TextButton(enabled = !busy, onClick = viewModel::resetState) { Text("New job") }
        }
        if (state.logPath != null) item { TextButton(onClick = { logs = true }) { Text("View installer log") } }
        if (state.flashedFiles.isNotEmpty()) item { Text("Recent installer results", style = MaterialTheme.typography.titleMedium) }
        items(state.flashedFiles) { entry ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(entry.fileName); Text(entry.flashDate, style = MaterialTheme.typography.bodySmall)
                Text(entry.outputSummary, color = if (entry.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            } }
        }
    }
    if (logs) OperationLogSheet(state.logPath, state.flashOutput) { logs = false }
}
