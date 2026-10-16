package com.valhalla.bolt.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import com.valhalla.bolt.viewModel.RestoreViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun RestoreScreen(modifier: Modifier = Modifier, viewModel: RestoreViewModel = koinViewModel(), initialSessionId: String? = null, onBackup: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val operations: OperationCoordinator = koinInject()
    val operation by operations.status.collectAsStateWithLifecycle()
    val busy = operation?.running == true || operation?.uncertain == true
    val context = LocalContext.current
    var sessionId by rememberSaveable { mutableStateOf(initialSessionId) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var review by rememberSaveable { mutableStateOf(false) }
    var both by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var result by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var logs by remember { mutableStateOf(false) }
    var providerError by remember { mutableStateOf<String?>(null) }
    val session = state.backupSessions.firstOrNull { it.id == sessionId }
    val selected = session?.files?.firstOrNull { it.partitionName == selectedName }
    LaunchedEffect(initialSessionId) { if (initialSessionId != null) sessionId = initialSessionId }
    LaunchedEffect(sessionId, session?.files) {
        if (session != null && session.files.none { it.partitionName == selectedName }) selectedName = session.files.firstOrNull()?.partitionName
    }
    BackHandler(sessionId != null || result) { sessionId = null; result = false; review = false }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.saveBackupDirectory(uri)
        }.onFailure { providerError = "Cannot retain backup directory permission: ${it.message}" }
    }
    val base = selected?.partitionName?.let { it.removeSuffix(PartitionPolicy.suffix(it) ?: "") }
    val bothAvailable = selected != null && base != null && PartitionPolicy.suffix(selected.partitionName) != null && state.activeSlotSuffix != null && selected.sessionImages.keys.containsAll(listOf("${base}_a", "${base}_b"))
    val inactive = selected?.partitionName?.let { PartitionPolicy.suffix(it)?.let { suffix -> state.activeSlotSuffix != null && suffix != state.activeSlotSuffix } } == true
    if (review && selected != null) AlertDialog(onDismissRequest = { review = false }, title = { Text("Review restore") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(session!!.displayTitle, style = MaterialTheme.typography.titleMedium); Text(session.subtitle, style = MaterialTheme.typography.bodySmall) }
            item { Text(if (both) "Original images: ${base}_a and ${base}_b" else "Original image: ${selected.partitionName}", style = MaterialTheme.typography.titleSmall) }
            item { Text("Device identity, target capacity and source digest are checked before the first write. Each written image is then verified by readback.") }
            if (bothAvailable) item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(checked = both, onCheckedChange = { both = it }); Text("Restore both original slots", style = MaterialTheme.typography.bodyMedium) } }
            if (inactive && !both) item { Text("This image is from the inactive slot. Select both original slots to restore this pair, or choose an active-slot image.", color = MaterialTheme.colorScheme.error) }
            item { Text("Writes cannot safely be cancelled.", style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(enabled = !busy && state.isRootAvailable && selected.validationError == null && (!inactive || both), onClick = {
            review = false; result = true
            viewModel.restoreSelectedBackup(selected, if (both) RestoreMode.BOTH_SLOTS else RestoreMode.ACTIVE_SLOT_ONLY)
        }) { Text(if (both) "Restore both original slots" else "Restore ${selected.partitionName}") } },
        dismissButton = { TextButton(onClick = { review = false }) { Text("Back") } })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val restoring = operation?.takeIf { it.kind == "Restore" && it.running }
        if (restoring != null) item {
            Text("Restoring originals.", style = MaterialTheme.typography.headlineLarge)
            Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(restoring.message, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                restoring.outcomes.forEach { Text("${it.partition} · ${it.state}", style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { logs = true }) { Text("View live log") }
            } }
        } else if (result && operation?.kind == "Restore") {
            item { OperationResultCard(operation!!) { logs = true } }
            item { OutlinedButton(onClick = { result = false; sessionId = null }, modifier = Modifier.fillMaxWidth()) { Text("Back to recovery bundles") } }
        } else if (session != null) {
            item { TextButton(onClick = { sessionId = null; review = false }) { Text("All recovery bundles") }; Text(session.displayTitle, style = MaterialTheme.typography.headlineMedium); Text(session.subtitle, style = MaterialTheme.typography.bodyMedium) }
            item { PartitionBadges(session.files.map { it.partitionName }) }
            item { Text("Choose one original image", style = MaterialTheme.typography.titleMedium) }
            items(session.files, key = { it.partitionName }) { backup ->
                OutlinedCard(onClick = { selectedName = backup.partitionName; both = false }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = selectedName == backup.partitionName, onClick = { selectedName = backup.partitionName; both = false })
                        Column(Modifier.weight(1f)) {
                            Text(backup.partitionName, style = MaterialTheme.typography.titleMedium, fontFamily = LocalTechnicalFontFamily.current)
                            backup.manifest?.images?.firstOrNull { it.partition == backup.partitionName }?.let { image -> Text("${readableSize(image.size)} · ${image.slot?.removePrefix("_")?.uppercase()?.let { "slot $it" } ?: "no slot suffix"}", style = MaterialTheme.typography.bodySmall) }
                            backup.validationError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item { Button(enabled = selected != null && selected.validationError == null && !busy && state.isRootAvailable, onClick = { both = false; review = true }, modifier = Modifier.fillMaxWidth()) { Text("Review restore of ${selectedName ?: "image"}") } }
            item { TextButton(onClick = { details = !details }) { Text("Manifest and source details") } }
            if (details) item {
                Text("Manifest present. Size, device and digests are validated at restore time.", style = MaterialTheme.typography.bodySmall)
                selected?.manifest?.images?.firstOrNull { it.partition == selected.partitionName }?.let { image -> Text("Target: ${image.targetPath}\nSHA-256: ${image.sha256}", style = MaterialTheme.typography.bodySmall, fontFamily = LocalTechnicalFontFamily.current) }
            }
        } else {
            item { Text("Your recovery library.", style = MaterialTheme.typography.headlineLarge); Text("Open a bundle to inspect its original images.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "AutoRecovery", "My backups").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) } } }
            val sessions = state.backupSessions.filter { filter == "All" || if (filter == "AutoRecovery") it.isAutomatic else !it.isAutomatic }
            if (sessions.isEmpty()) item {
                Card { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(32.dp))
                    Text("No recovery bundles here yet", style = MaterialTheme.typography.titleMedium)
                    Text("Automatic recovery is saved before installation. You can also create a backup or choose its directory.")
                    OutlinedButton(onClick = onBackup) { Text("Create backup") }
                } }
            }
            items(sessions, key = { it.id + it.source }) { bundle -> RecoveryBundleCard(bundle) { sessionId = bundle.id; details = false } }
            item { OutlinedButton(enabled = !busy, onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Choose backup directory") }; TextButton(enabled = !busy, onClick = { viewModel.checkRootAvailability(); viewModel.refresh() }) { Text("Refresh bundles") } }
        }
        if (!state.isRootAvailable) item { Text("Root is required to restore. Bundles remain available for inspection.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        providerError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
    if (logs) OperationLogSheet(operation?.logPath, operation?.tail.orEmpty()) { logs = false }
}
