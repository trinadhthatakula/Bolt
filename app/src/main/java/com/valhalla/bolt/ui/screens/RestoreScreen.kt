package com.valhalla.bolt.ui.screens

import android.R.attr.text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.R
import com.valhalla.bolt.model.BackupFile
import com.valhalla.bolt.model.RestoreMode
import com.valhalla.bolt.model.RestoreState
import com.valhalla.bolt.ui.theme.firaFontFamily
import com.valhalla.bolt.viewModel.RestoreViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    modifier: Modifier = Modifier,
    viewModel: RestoreViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var backupToRestore by remember { mutableStateOf<BackupFile?>(null) }
    var finalBackUp by remember { mutableStateOf<BackupFile?>(null) }
    var showLoggerDialog by remember { mutableStateOf(false) }

    // Confirmation Dialog for the dangerous restore action
    if (backupToRestore != null) {
        AlertDialog(
            onDismissRequest = { backupToRestore = null },
            title = { Text("⚠️ Confirm Restore") },
            text = { Text("Are you sure you want to restore '${backupToRestore!!.partitionName}'? This can make your device unbootable if the backup is incompatible. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        finalBackUp = backupToRestore
                        backupToRestore = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToRestore = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (finalBackUp != null)
        finalBackUp?.let { fb ->
            if (uiState.activeSlotSuffix != null) {
                AbRestoreDialog(
                    backupFile = fb,
                    onDismiss = {
                        finalBackUp = null
                        backupToRestore = null
                    },
                    onRestore = { mode ->
                        viewModel.restoreSelectedBackup(fb, mode)
                        backupToRestore = null
                    }
                )
            } else {
                // Otherwise, show the simple confirmation dialog
                SimpleRestoreDialog(
                    backupFile = fb,
                    onDismiss = { backupToRestore = null },
                    onRestore = {
                        viewModel.restoreSelectedBackup(fb, RestoreMode.ACTIVE_SLOT_ONLY)
                        backupToRestore = null
                    }
                )
            }
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.unarchive),
                "Restore Icon",
                modifier = Modifier.size(36.dp)
            )
            Text(
                "Restore Backup ⚠️ ",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            if (uiState.processingSteps.isNotEmpty()) {
                IconButton(onClick = { showLoggerDialog = true }) {
                    Icon(painterResource(R.drawable.terminal), "Open Logger")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (uiState.restoreState) {
            RestoreState.LISTING, RestoreState.RESTORING -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            RestoreState.ERROR -> Text(
                uiState.errorMessage ?: "An unknown error occurred.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            else -> {
                if (viewModel.getBackUpDirectory() == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No backup location has been set.\n\nPlease perform a backup first to set the directory.",
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (uiState.backupSessions.isEmpty()) {
                    Text(
                        "No backups found in the saved directory.",
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        uiState.backupSessions.forEach { session ->
                            item {
                                Text(
                                    text = session.sessionName.replace("_", " ")
                                        .replace("-", ":"),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                )
                                HorizontalDivider()
                            }
                            // List of backup files within that session
                            items(session.files) { backup ->
                                BackupItem(
                                    backup = backup,
                                    onRestoreClicked = { backupToRestore = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoggerDialog) {
        ModalBottomSheet(onDismissRequest = { showLoggerDialog = false }) {
            // ... (Logger UI - This can be extracted into a common composable)
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(uiState.processingSteps) { step ->
                    Text(
                        text = "${step.title}: ${step.description}",
                        fontFamily = firaFontFamily,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupItem(backup: BackupFile, onRestoreClicked: (BackupFile) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(backup.partitionName, style = MaterialTheme.typography.titleMedium)
                Text(
                    backup.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { onRestoreClicked(backup) }) {
                Text("Restore")
            }
        }
    }
}


@Composable
fun AbRestoreDialog(
    backupFile: BackupFile,
    onDismiss: () -> Unit,
    onRestore: (RestoreMode) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "⚠️ A/B Restore Option",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(5.dp)
                )
                Text(
                    "You are on an A/B device. Restoring to the inactive slot can be useful but is an advanced operation.\n\nHow do you want to restore '${backupFile.partitionName}'?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp)
                ) {
                    Button(onClick = { onRestore(RestoreMode.BOTH_SLOTS) }) {
                        Text("Both Slots")
                    }
                    Button(onClick = { onRestore(RestoreMode.ACTIVE_SLOT_ONLY) }) {
                        Text("Active Slot")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}


@Composable
fun SimpleRestoreDialog(
    backupFile: BackupFile,
    onDismiss: () -> Unit,
    onRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ Confirm Restore") },
        text = { Text("Are you sure you want to restore '${backupFile.partitionName}'? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onRestore,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Restore Now") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}