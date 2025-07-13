package com.valhalla.bolt.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valhalla.bolt.R
import com.valhalla.bolt.model.FlashedFile
import com.valhalla.bolt.model.FlashingState
import com.valhalla.bolt.model.HomeUiState
import com.valhalla.bolt.ui.theme.firaFontFamily
import com.valhalla.bolt.viewModel.FlasherViewModel
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FlashScreen(
    modifier: Modifier = Modifier,
    viewModel: FlasherViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val showRebootConfirmation by viewModel.showRebootConfirmation.collectAsStateWithLifecycle()
    if (showRebootConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelReboot() },
            title = { Text("Confirm Reboot") },
            text = { Text("Are you sure you want to reboot your device now?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rebootDevice()
                        viewModel.cancelReboot()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reboot Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelReboot() }) {
                    Text("Cancel")
                }
            }
        )
    }


    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.pickZipFile(it) } }
    )
    var showLoggerDialog by remember { mutableStateOf(false) }

    // Show error messages in snackbar
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            // Clear error after showing
            viewModel.resetState()
        }
    }
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.launcher_foreground),
                "Bolt Icon",
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Bolt Kernel Flasher",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier
                    .weight(1f)
            )
            if (uiState.processingSteps.isNotEmpty())
                IconButton(
                    onClick = {
                        showLoggerDialog = true
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.terminal),
                        "Open Logger"
                    )
                }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                !uiState.isRootCheckComplete -> {
                    ContainedLoadingIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Checking root access...")
                }

                !uiState.isRootAvailable -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Root access not available",
                            color = Color.Red,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { viewModel.checkRootAvailability() },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.restart_alt_24px),
                                "Retry"
                            )
                        }
                    }

                    Text("This app requires root access to flash kernels")
                }

                else -> {
                    // Main content when root is available
                    KernelFlasherContent(
                        uiState = uiState,
                        onPickZip = {
                            viewModel.resetState()
                            filePicker.launch(arrayOf("application/zip"))
                        },
                        onFlash = {
                            showLoggerDialog = true
                            viewModel.flashKernel()
                        },
                        onReboot = { viewModel.confirmReboot() }, // New reboot callback
                        modifier = Modifier.weight(1f)
                    )

                    if (uiState.flashedFiles.isNotEmpty()) {
                        FlashHistoryList(
                            flashedFiles = uiState.flashedFiles,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.5f)
                        )
                    }
                }
            }
        }

    }

    if (showLoggerDialog) {
        ModalBottomSheet(
            onDismissRequest = {
                showLoggerDialog = false
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator()
                Text(
                    "Bolt Logs",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(5.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
            ) {

                items(uiState.processingSteps) { step ->
                    var expanded by remember {
                        mutableStateOf(true)
                    }
                    Row(
                        modifier = Modifier
                            .padding(3.dp)
                            .clickable {
                                expanded = !expanded
                            }) {
                        Column {
                            Text(
                                step.title,
                                style = MaterialTheme.typography.bodySmallEmphasized,
                                fontFamily = firaFontFamily
                            )
                            if (expanded)
                                Text(
                                    "-${step.description}",
                                    style = MaterialTheme.typography.bodySmallEmphasized,
                                    fontFamily = firaFontFamily
                                )
                        }
                    }
                }
            }
            if (uiState.flashingState == FlashingState.ERROR || uiState.flashingState == FlashingState.SUCCESS || uiState.flashingState == FlashingState.SUCCESS_REBOOT_NEEDED) {
                Button(
                    onClick = { showLoggerDialog = false },
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text("Done")
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KernelFlasherContent(
    uiState: HomeUiState,
    onPickZip: () -> Unit,
    onFlash: () -> Unit,
    onReboot: () -> Unit, // New reboot callback
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState.flashingState) {

            FlashingState.VALIDATING_ZIP -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Validating zip file...", fontSize = 18.sp)
                    Spacer(Modifier.height(24.dp))
                    LoadingIndicator()
                }
            }

            FlashingState.IDLE -> {
                Button(
                    onClick = onPickZip,
                    enabled = uiState.copiedZipPath == null
                ) {
                    Text("Select Kernel Zip")
                }

                uiState.copiedZipPath?.let { path ->
                    Spacer(Modifier.height(24.dp))
                    Text("Selected: ${File(path).name}")

                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onFlash) {
                        Text("Flash Kernel")
                    }
                }
            }

            FlashingState.COPYING_ZIP,
            FlashingState.EXTRACTING,
            FlashingState.FLASHING -> {
                Text(
                    text = when (uiState.flashingState) {
                        FlashingState.COPYING_ZIP -> "Copying zip file..."
                        FlashingState.EXTRACTING -> "Extracting files..."
                        FlashingState.FLASHING -> "Flashing kernel..."
                        else -> "Processing..."
                    },
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(24.dp))
                LoadingIndicator()
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 16.dp)
                )
            }

            FlashingState.SUCCESS -> {
                Text("✓ Flashing Successful", color = Color.Green, fontSize = 20.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { /* Reset state */ }) {
                    Text("Flash Another Kernel")
                }
            }

            FlashingState.ERROR -> {
                Text("Flashing Failed", color = Color.Red, fontSize = 20.sp)
                // Show specific instructions for invalid zip
                if (uiState.errorMessage == "Invalid AnyKernel3 zip") {
                    Text("Please select a valid AnyKernel3 zip file containing:")
                    Text("- anykernel.sh at root level", fontWeight = FontWeight.Bold)
                    Text("- or update-binary in META-INF", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onPickZip() }) {
                    Text("Try Again")
                }
            }

            FlashingState.SUCCESS_REBOOT_NEEDED -> {
                Text("✓ Flashing Successful", color = Color.Green, fontSize = 20.sp)
                Text("Reboot required to apply changes", color = Color.Yellow)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onReboot,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Reboot Device")
                }
            }

            FlashingState.REBOOTING -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rebooting device...", fontSize = 18.sp)
                    Spacer(Modifier.height(24.dp))
                    LoadingIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "If device doesn't reboot automatically, please reboot manually",
                        color = Color.Yellow
                    )
                }
            }

            else -> {}
        }
    }
}

@Composable
fun FlashHistoryList(
    flashedFiles: List<FlashedFile>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Flash History:", fontWeight = FontWeight.Bold)

        if (flashedFiles.isEmpty()) {
            Text("No flash history available", modifier = Modifier.padding(top = 8.dp))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .weight(1f)
            ) {
                items(flashedFiles) { file ->
                    HistoryItem(file = file)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryItem(file: FlashedFile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                /*Text(
                    text = if (file.success) " ✓ " else " ✗ ",
                    color = if (file.success) Color.Green else Color.Red,
                    fontSize = 20.sp
                )*/
            }

            Text(
                file.flashDate,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Text(file.outputSummary, fontSize = 14.sp)
        }
    }
}