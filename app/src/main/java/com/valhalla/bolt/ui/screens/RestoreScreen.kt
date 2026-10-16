package com.valhalla.bolt.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.model.*
import com.valhalla.bolt.viewModel.RestoreViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun RestoreScreen(modifier: Modifier = Modifier, viewModel: RestoreViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val operations: OperationCoordinator = koinInject()
    val operation by operations.status.collectAsStateWithLifecycle()
    val busy = operation?.running == true || operation?.uncertain == true
    val context = LocalContext.current
    var selected by remember { mutableStateOf<BackupFile?>(null) }
    var both by remember { mutableStateOf(false) }
    var providerError by remember { mutableStateOf<String?>(null) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.saveBackupDirectory(uri)
        }.onFailure { providerError = "Cannot retain backup directory permission: ${it.message}" }
    }
    selected?.let { backup -> AlertDialog(
        onDismissRequest = { selected = null },
        title = { Text("Confirm verified restore") },
        text = { Column {
            Text("Restore ${backup.partitionName} from its verified manifest? Bolt will check the device, target capacity and image digest before writing, then verify readback. Writes cannot safely be cancelled.")
            val base = backup.partitionName.removeSuffix(PartitionPolicy.suffix(backup.partitionName) ?: "")
            if (PartitionPolicy.suffix(backup.partitionName) != null && state.activeSlotSuffix != null && backup.sessionImages.keys.containsAll(listOf("${base}_a", "${base}_b"))) {
                Row { Checkbox(checked = both, onCheckedChange = { both = it }); Text("Restore both original slot images") }
            }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            selected = null // Consume the confirmation before scheduling any write.
            viewModel.restoreSelectedBackup(backup, if (both) RestoreMode.BOTH_SLOTS else RestoreMode.ACTIVE_SLOT_ONLY)
        }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }
    ) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Restore verified backup", style = MaterialTheme.typography.headlineSmall) }
        item { Row {
            TextButton(enabled = !busy, onClick = { folder.launch(null) }) { Text("Choose backup directory") }
            TextButton(enabled = !busy, onClick = { viewModel.checkRootAvailability(); viewModel.refresh() }) { Text("Refresh") }
        } }
        if (!state.isRootAvailable) item { Text("Root access is required to restore. Backups can still be inspected.") }
        state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        providerError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.backupSessions.isEmpty()) item { Text("No backups found. Automatic pre-flash recovery and backups in the selected directory appear here.") }
        state.backupSessions.forEach { session ->
            item { Text(session.sessionName, style = MaterialTheme.typography.titleMedium) }
            items(session.files) { backup ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(backup.partitionName)
                    Text(backup.validationError ?: "Manifest present. Device, size and digest are checked before restore.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !busy && state.isRootAvailable && backup.validationError == null, onClick = { both = false; selected = backup }) { Text("Restore ${backup.partitionName}") }
                } }
            }
        }
    }
}
