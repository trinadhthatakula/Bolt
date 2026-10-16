package com.valhalla.bolt.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.model.*
import com.valhalla.bolt.ui.theme.LocalTechnicalFontFamily
import com.valhalla.bolt.viewModel.BackUpViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun BackUpScreen(modifier: Modifier = Modifier, viewModel: BackUpViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val operations: OperationCoordinator = koinInject()
    val operation by operations.status.collectAsStateWithLifecycle()
    val busy = operation?.running == true || operation?.uncertain == true
    val context = LocalContext.current
    var names by rememberSaveable { mutableStateOf(listOf<String>()) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var review by rememberSaveable { mutableStateOf(false) }
    var showingResult by rememberSaveable { mutableStateOf(false) }
    var logs by remember { mutableStateOf(false) }
    var providerError by remember { mutableStateOf<String?>(null) }
    val selected = (state.recommendedPartitions + state.otherPartitions).filter { it.name in names }
    val destination = state.savedBackupDirectoryUri
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.saveBackupDirectory(uri)
        }.onFailure { providerError = "Cannot access backup directory: ${it.message}" }
    }
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text("Review backup") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("${selected.size} original partition images · ${readableSize(selected.sumOf { it.sizeBytes })}", style = MaterialTheme.typography.titleMedium) }
            item { PartitionBadges(selected.map { it.name }) }
            item { Text("Images are exported to your selected directory and their digests are checked. A complete manifest is saved last.") }
        } },
        confirmButton = { TextButton(enabled = !busy && selected.isNotEmpty() && destination != null && state.isRootAvailable, onClick = { review = false; showingResult = true; viewModel.startBackup(selected) }) { Text("Save backup") } },
        dismissButton = { TextButton(onClick = { review = false }) { Text("Back") } })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (operation?.kind == "Backup" && operation?.running == true) item {
            Text("Saving your originals.", style = MaterialTheme.typography.headlineLarge)
            Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(operation!!.message, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                operation!!.outcomes.forEach { Text("${it.partition} · ${it.state}", style = MaterialTheme.typography.bodySmall, fontFamily = LocalTechnicalFontFamily.current) }
                TextButton(onClick = { logs = true }) { Text("View live log") }
            } }
        } else if (showingResult && operation?.kind == "Backup") {
            item { OperationResultCard(operation!!) { logs = true } }
            item { TextButton(onClick = { showingResult = false; names = emptyList() }) { Text("Create another backup") } }
        } else {
            item { Text("Keep your originals.", style = MaterialTheme.typography.headlineLarge) }
            item {
                Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.FolderOpen, null)
                    Text("Backup destination", style = MaterialTheme.typography.titleMedium)
                    Text(if (destination == null) "Choose where to save the bundle" else "Selected backup directory", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(enabled = !busy, onClick = { folder.launch(null) }) { Text(if (destination == null) "Choose directory" else "Change directory") }
                } }
            }
            if (!state.isRootCheckComplete || state.backupRestoreState in setOf(BackupRestoreState.CHECKING_ROOT, BackupRestoreState.LISTING_PARTITIONS)) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Discovering available images…") }
            if (state.isRootCheckComplete && !state.isRootAvailable) item { Text("Root is required to read partition images."); TextButton(enabled = !busy, onClick = viewModel::checkRootAvailability) { Text("Check root again") } }
            if (state.recommendedPartitions.isNotEmpty()) item { Text("Recommended images", style = MaterialTheme.typography.titleMedium) }
            items(state.recommendedPartitions, key = { it.name }) { partition -> BackupPartitionCard(partition, partition.name in names, !busy) { checked -> names = if (checked) names + partition.name else names - partition.name } }
            if (state.otherPartitions.isNotEmpty()) item { TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide other partitions" else "Other partitions · ${state.otherPartitions.size}") } }
            if (advanced) items(state.otherPartitions, key = { it.name }) { partition -> BackupPartitionCard(partition, partition.name in names, !busy) { checked -> names = if (checked) names + partition.name else names - partition.name } }
            item { Button(enabled = !busy && state.isRootAvailable && selected.isNotEmpty() && destination != null, onClick = { review = true }, modifier = Modifier.fillMaxWidth()) { Text("Review ${selected.size} images") } }
        }
        (providerError ?: state.errorMessage)?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
    }
    if (logs) OperationLogSheet(operation?.logPath, operation?.tail.orEmpty()) { logs = false }
}

@Composable
private fun BackupPartitionCard(partition: Partition, selected: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    OutlinedCard(onClick = { if (enabled) onSelect(!selected) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = onSelect, enabled = enabled)
            Column { Text(partition.name, style = MaterialTheme.typography.titleMedium, fontFamily = LocalTechnicalFontFamily.current); Text(readableSize(partition.sizeBytes), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
