package com.valhalla.bolt.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.R
import com.valhalla.bolt.model.BackupRestoreState
import com.valhalla.bolt.model.BackupUiState
import com.valhalla.bolt.model.Partition
import com.valhalla.bolt.ui.theme.firaFontFamily
import com.valhalla.bolt.viewModel.BackUpViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackUpScreen(
    modifier: Modifier = Modifier,
    viewModel: BackUpViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLoggerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 1. Header - Similar to FlashScreen
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.archive), // A suggested icon for backup
                "Backup Icon",
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Backup",
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

        when {
            uiState.backupRestoreState == BackupRestoreState.CHECKING_ROOT -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Checking root access...")
                }
            }

            !uiState.isRootAvailable && uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Root Access Required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = uiState.errorMessage ?: "This feature requires root.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            else -> {
                BackupRestoreContent(
                    uiState = uiState,
                    onStartBackup = { partitions ->
                        showLoggerDialog = true
                        viewModel.startBackup(partitions)
                    },
                    onSaveNewLocationAndBackup = { partitions, uri ->
                        showLoggerDialog = true
                        viewModel.saveBackupDirectoryAndStartBackup(partitions, uri)
                    }
                )
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
fun BackupRestoreContent(
    uiState: BackupUiState,
    onStartBackup: (List<Partition>) -> Unit,
    onSaveNewLocationAndBackup: (List<Partition>, Uri) -> Unit
) {
    var selectedPartitions by remember { mutableStateOf<List<Partition>>(emptyList()) }

    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val backupLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
                if (selectedPartitions.isNotEmpty()) {
                    onSaveNewLocationAndBackup(selectedPartitions.toList(), uri)

                }
            }
        }
    )

    var otherPartitionsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        uiState.activeSlotSuffix?.let { slot ->
            Text(
                text = "A/B Partition Scheme Detected (Active slot: $slot)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        if (uiState.backupRestoreState == BackupRestoreState.LISTING_PARTITIONS) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.recommendedPartitions) { partition ->
                    PartitionItem(
                        partition = partition,
                        isSelected = partition in selectedPartitions,
                        onSelectionChanged = {
                            selectedPartitions = if (it) {
                                selectedPartitions + partition
                            } else {
                                selectedPartitions - partition
                            }
                        }
                    )
                }
                if (uiState.otherPartitions.isNotEmpty()) {
                    item {
                        ExpandableHeader(
                            title = "Other Partitions (${uiState.otherPartitions.size})",
                            isExpanded = otherPartitionsExpanded,
                            onClick = { otherPartitionsExpanded = !otherPartitionsExpanded }
                        )
                    }
                }

                // 3. Show other partitions only if expanded
                if (otherPartitionsExpanded) {
                    items(uiState.otherPartitions) { partition ->
                        PartitionItem(
                            partition = partition,
                            isSelected = partition in selectedPartitions,
                            onSelectionChanged = {
                                selectedPartitions = if (it) {
                                    selectedPartitions + partition
                                } else {
                                    selectedPartitions - partition
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (uiState.savedBackupDirectoryUri != null) {
                    // If location is known, start backup directly
                    onStartBackup(selectedPartitions)
                } else {
                    // If location is unknown, launch the directory picker
                    backupLocationLauncher.launch(null)
                }
            },
            enabled = selectedPartitions.isNotEmpty() && uiState.backupRestoreState == BackupRestoreState.IDLE,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Backup Selected (${selectedPartitions.size}) Partitions")
        }
    }
}

@Composable
fun ExpandableHeader(
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.arrow_drop_down),
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.graphicsLayer(rotationZ = rotationAngle)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartitionItem(
    partition: Partition,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { onSelectionChanged(!isSelected) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = partition.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = firaFontFamily
            )
            Checkbox(
                checked = isSelected,
                onCheckedChange = null // Click handled by the Card
            )
        }
    }
}