package com.valhalla.bolt.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.model.*
import com.valhalla.bolt.ui.theme.LocalTechnicalFontFamily
import com.valhalla.bolt.viewModel.FlasherViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun FlashScreen(modifier: Modifier = Modifier, viewModel: FlasherViewModel = koinViewModel(), incomingUri: Uri? = null, onImportConsumed: () -> Unit = {}, onRecovery: () -> Unit = {}, onDone: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val operations: OperationCoordinator = koinInject()
    val operation by operations.status.collectAsStateWithLifecycle()
    val reboot by viewModel.showRebootConfirmation.collectAsStateWithLifecycle()
    var logs by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::pickZipFile) }
    val busy = operation?.running == true || operation?.uncertain == true || state.isZipValidating || state.flashingState == FlashingState.VALIDATING_ZIP
    val choose: () -> Unit = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
    if (incomingUri != null) AlertDialog(onDismissRequest = onImportConsumed, title = { Text("Import AnyKernel ZIP?") },
        text = { Text("Bolt will copy and validate this document. Installation requires a separate review.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.pickZipFile(incomingUri); onImportConsumed() }) { Text("Import ZIP") } },
        dismissButton = { TextButton(onClick = onImportConsumed) { Text("Cancel") } })
    state.reviewText?.let { review -> AlertDialog(onDismissRequest = viewModel::dismissReview, title = { Text("Review install") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(state.fileName, style = MaterialTheme.typography.titleMedium) }
            item { Text("${Build.MODEL} · ${state.activeSlot?.let { "slot ${it.removePrefix("_").uppercase()}" } ?: "no slot suffix"}") }
            item { Text("Archive, root, power and recovery space checked", style = MaterialTheme.typography.bodyMedium) }
            item { Text("AutoRecovery · ${state.recoveryTargets.size} partitions", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp)); PartitionBadges(state.recoveryTargets) }
            item { Text("Your originals are saved before the installer starts. The saved bundle appears in Recovery.", style = MaterialTheme.typography.bodyMedium) }
            item { Text(review, style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = viewModel::flashKernel) { Text("Flash kernel") } },
        dismissButton = { TextButton(onClick = viewModel::dismissReview) { Text("Back") } }) }
    if (reboot) AlertDialog(onDismissRequest = viewModel::cancelReboot, title = { Text("Reboot device?") },
        text = { Text("Restart now to load the installed kernel. Your recovery bundle and installer log are saved.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.cancelReboot(); viewModel.rebootDevice() }) { Text("Reboot now") } },
        dismissButton = { TextButton(onClick = viewModel::cancelReboot) { Text("Later") } })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val active = operation?.takeIf { it.kind == "Flash" && it.running }
        if (active != null) {
            item {
                Text(if (active.phase == "Executing") "Installing your kernel." else "Saving your way back.", style = MaterialTheme.typography.headlineLarge)
                Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (active.phase == "Executing") "Original installer running" else active.message, style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    val saved = active.outcomes.count { it.state == "Backed up" }
                    if (active.outcomes.isNotEmpty()) Text("$saved original images saved", style = MaterialTheme.typography.labelMedium)
                    active.outcomes.forEach { Text("${it.partition} · ${it.state}", style = MaterialTheme.typography.bodySmall, fontFamily = LocalTechnicalFontFamily.current) }
                    Text("The operation continues if you leave this screen. Writes cannot safely be cancelled.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { logs = true }) { Icon(Icons.Outlined.Terminal, null); Spacer(Modifier.width(8.dp)); Text("View live log") }
                } }
            }
        } else if (state.flashingState in setOf(FlashingState.SUCCESS, FlashingState.SUCCESS_REBOOT_NEEDED)) {
            val journey = state.journey
            item {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(journey?.statusLabel ?: "Kernel installed.", style = MaterialTheme.typography.headlineLarge)
                Text(journey?.fileName ?: state.fileName.ifEmpty { "Previous kernel" }, style = MaterialTheme.typography.titleMedium)
            }
            item { Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Installer completed · exit 0", style = MaterialTheme.typography.labelLarge)
                Text(when (journey?.bootStatus) {
                    BootStatus.REBOOT_OBSERVED -> "You’re back in Bolt after a detected device restart. How is the kernel working?"
                    BootStatus.USER_CONFIRMED -> if (journey.bootObservedAt == null) "You confirmed that you rebooted and the kernel is working." else "Your feedback is saved with this install."
                    BootStatus.ISSUE_REPORTED -> "Your feedback is saved. Inspect the originals and installer log before another operation."
                    else -> "Reboot when you’re ready to apply the kernel."
                }, style = MaterialTheme.typography.bodyLarge)
                if (state.flashingState == FlashingState.SUCCESS_REBOOT_NEEDED) Button(enabled = !busy, onClick = viewModel::confirmReboot, modifier = Modifier.fillMaxWidth()) { Text("Reboot device") }
                if (journey != null && journey.bootStatus == BootStatus.REBOOT_PENDING) OutlinedButton(onClick = { viewModel.confirmKernelWorking(true) }, modifier = Modifier.fillMaxWidth()) { Text("I rebooted and it’s working") }
                if (journey?.bootStatus == BootStatus.REBOOT_OBSERVED) Button(onClick = { viewModel.confirmKernelWorking(true) }, modifier = Modifier.fillMaxWidth()) { Text("Working well") }
                if (journey != null && journey.bootStatus != BootStatus.ISSUE_REPORTED) TextButton(onClick = { viewModel.confirmKernelWorking(false) }) { Text("I noticed a problem") }
            } } }
            item { OutlinedButton(onClick = onRecovery, modifier = Modifier.fillMaxWidth()) { Text("Open saved recovery") } }
            item { TextButton(onClick = { details = !details }) { Text("Install details") } }
            if (details) item {
                Text("Installer completion, device restart and your feedback are separate observations. A restart does not independently verify every kernel feature.", style = MaterialTheme.typography.bodySmall)
                journey?.bootObservedAt?.let { Text("Restart observed ${readableDate(it)}", style = MaterialTheme.typography.bodySmall) }
                if (journey?.bootIdBefore == null) Text("This install has no recorded pre-flash boot identity. Its reboot can be confirmed by you.", style = MaterialTheme.typography.bodySmall)
            }
            item { TextButton(enabled = !busy, onClick = { viewModel.resetState(); details = false }) { Text("Install another kernel") }; TextButton(onClick = onDone) { Text("Back to Overview") } }
        } else {
            item { Text(if (state.copiedZipPath == null) "Your next kernel." else "Ready for review.", style = MaterialTheme.typography.headlineLarge) }
            item {
                Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.Bolt, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (state.copiedZipPath == null) "Choose an AnyKernel ZIP" else state.fileName, style = MaterialTheme.typography.titleLarge)
                    Text("Bolt keeps your original partition images together in an automatic recovery bundle.", style = MaterialTheme.typography.bodyMedium)
                    Button(enabled = !busy, onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(if (state.copiedZipPath == null) "Select kernel ZIP" else "Change ZIP") }
                    if (state.copiedZipPath != null) {
                        OutlinedButton(enabled = !busy && state.isRootAvailable, onClick = viewModel::prepareFlash, modifier = Modifier.fillMaxWidth()) { Text("Review install") }
                        TextButton(onClick = { details = !details }) { Text("Technical details") }
                        if (details) Text("SHA-256: ${state.sha256}", style = MaterialTheme.typography.bodySmall, fontFamily = LocalTechnicalFontFamily.current)
                    }
                } }
            }
            if (state.isZipValidating || state.flashingState == FlashingState.VALIDATING_ZIP) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking document and device…") }
            if (!state.isRootCheckComplete) item { Text("Checking root access…") }
            else if (!state.isRootAvailable) item {
                Text("Root is required to install. You can still choose and inspect a ZIP.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(enabled = !busy, onClick = viewModel::checkRootAvailability) { Text("Check root again") }
            }
            if (state.flashingState == FlashingState.ERROR) item { OutlinedButton(onClick = onRecovery) { Text("Inspect recovery") }; TextButton(enabled = !busy, onClick = viewModel::resetState) { Text("Choose another ZIP") } }
        }
        state.errorMessage?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (state.logPath != null && active == null) item { TextButton(onClick = { logs = true }) { Text("View installer log") } }
    }
    if (logs) OperationLogSheet(state.logPath ?: operation?.logPath, if (operation?.kind == "Flash") operation?.tail.orEmpty() else state.flashOutput) { logs = false }
}
