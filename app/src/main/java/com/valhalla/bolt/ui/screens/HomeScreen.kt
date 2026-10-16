package com.valhalla.bolt.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.model.*
import com.valhalla.bolt.viewModel.FlasherViewModel
import com.valhalla.bolt.viewModel.RestoreViewModel
import com.valhalla.bolt.ui.theme.LocalTechnicalFontFamily
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier, incomingUri: Uri? = null, onImportConsumed: () -> Unit = {}) {
    var route by rememberSaveable { mutableStateOf("overview") }
    var initialBundle by rememberSaveable { mutableStateOf<String?>(null) }
    val flasher: FlasherViewModel = koinViewModel()
    val restore: RestoreViewModel = koinViewModel()
    val flash by flasher.uiState.collectAsStateWithLifecycle()
    val recovery by restore.uiState.collectAsStateWithLifecycle()
    val operations: OperationCoordinator = koinInject()
    val status by operations.status.collectAsStateWithLifecycle()
    val journeys: FlashJourneyStore = koinInject()
    val records by journeys.state.collectAsStateWithLifecycle()
    var logs by remember { mutableStateOf(false) }
    var logPath by remember { mutableStateOf<String?>(null) }
    var logTail by remember { mutableStateOf("") }
    var acknowledge by remember { mutableStateOf(false) }
    val openLogs: (String?, String) -> Unit = { path, tail -> logPath = path; logTail = tail; logs = true }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, flasher) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { flasher.refreshBootStatus(); restore.refresh() } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(incomingUri) { if (incomingUri != null) route = "flash" }
    val back: () -> Unit = { route = "overview"; initialBundle = null }
    BackHandler(route != "overview") { back() }
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text(when (route) {
            "settings" -> "Appearance"
            "flash" -> "Install kernel"
            "backup" -> "Create backup"
            else -> "Bolt"
        }, style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
            if (route !in listOf("overview", "recovery", "activity")) IconButton(onClick = back) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Overview")
            }
        }, actions = {
            if (route != "settings") IconButton(onClick = { route = "settings" }) { Icon(Icons.Outlined.Settings, "Appearance settings") }
        })
    }, bottomBar = {
        if (route != "settings") NavigationBar {
            listOf(Triple("overview", Icons.Outlined.Dashboard, "Overview"), Triple("recovery", Icons.Outlined.Restore, "Recovery"), Triple("activity", Icons.Outlined.History, "Activity")).forEach { (id, icon, label) ->
                NavigationBarItem(selected = route == id || id == "overview" && route in listOf("flash", "backup"),
                    onClick = { route = id; initialBundle = null }, icon = { Icon(icon, label) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            status?.takeIf { it.running || it.uncertain }?.let { active ->
                Surface(onClick = { route = if (active.kind == "Restore") "recovery" else if (active.kind == "Backup") "backup" else "flash" }, color = if (active.uncertain) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(if (active.uncertain) "Operation needs review" else "${active.kind} · ${active.phase}", style = MaterialTheme.typography.labelLarge)
                        if (active.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openLogs(active.logPath, active.tail) }) { Text("View log") }
                            if (active.uncertain) TextButton(onClick = { acknowledge = true }) { Text("Review outcome") }
                        }
                    }
                }
            }
            when (route) {
                "flash" -> FlashScreen(viewModel = flasher, incomingUri = incomingUri, onImportConsumed = onImportConsumed, onRecovery = { route = "recovery" }, onDone = back)
                "recovery" -> RestoreScreen(viewModel = restore, initialSessionId = initialBundle, onBackup = { route = "backup" })
                "backup" -> BackUpScreen()
                "settings" -> ThemeSettingsScreen()
                "activity" -> ActivityScreen(flash.flashedFiles, records, status, openLogs, onCurrentInstall = { flasher.showCurrentInstall(); route = "flash" })
                else -> CockpitOverview(flash, recovery.backupSessions, status,
                    onInstall = { route = "flash" }, onRecovery = { route = "recovery" }, onBackup = { route = "backup" },
                    onBundle = { initialBundle = it.id; route = "recovery" })
            }
        }
    }
    if (logs) OperationLogSheet(logPath, status?.takeIf { it.logPath == logPath }?.tail ?: logTail) { logs = false }
    if (acknowledge) AlertDialog(onDismissRequest = { acknowledge = false }, title = { Text("Review recovery state") },
        text = { Text("Inspect the retained log and original images, and check whether the installer is still running. Acknowledgment does not undo or verify partition changes; Bolt will not replay this operation.") },
        confirmButton = { TextButton(onClick = { acknowledge = false; operations.acknowledgeInterruption() }) { Text("I reviewed the recovery state") } },
        dismissButton = { TextButton(onClick = { acknowledge = false }) { Text("Keep writes blocked") } })
}

@Composable
private fun CockpitOverview(flash: FlashScreenUiState, sessions: List<BackupSession>, operation: OperationStatus?, onInstall: () -> Unit, onRecovery: () -> Unit, onBackup: () -> Unit, onBundle: (BackupSession) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (!flash.isRootCheckComplete) "Checking root" else if (flash.isRootAvailable) "Root ready" else "Root unavailable", style = MaterialTheme.typography.labelLarge)
                    Text(flash.deviceName, style = MaterialTheme.typography.headlineMedium)
                    Text(flash.kernelRelease ?: "Kernel details unavailable", style = MaterialTheme.typography.bodySmall, fontFamily = LocalTechnicalFontFamily.current)
                    flash.activeSlot?.let { Text("Current slot ${it.removePrefix("_").uppercase()}", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        item { Text("Make it yours.", style = MaterialTheme.typography.headlineLarge) }
        if (operation?.kind == "Flash" && operation.phase == "Completed" && flash.journey != null) item {
            Card(onClick = onInstall, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(flash.journey!!.statusLabel, style = MaterialTheme.typography.titleMedium)
                    Text(flash.journey!!.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Open install details", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            BoxWithConstraints {
                val stack = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
                val install: @Composable (Modifier) -> Unit = { tileModifier ->
                    CockpitTile(tileModifier, Icons.Outlined.Bolt, if (flash.copiedZipPath == null) "Install kernel" else "Continue install",
                        if (flash.copiedZipPath == null) "Choose an AnyKernel ZIP" else flash.fileName, onInstall)
                }
                val recover: @Composable (Modifier) -> Unit = { tileModifier ->
                    CockpitTile(tileModifier, Icons.Outlined.Restore, "Recovery", "${sessions.size} saved ${if (sessions.size == 1) "bundle" else "bundles"}", onRecovery)
                }
                if (stack) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { install(Modifier.fillMaxWidth()); recover(Modifier.fillMaxWidth()) }
                else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) { install(Modifier.weight(1f).fillMaxHeight()); recover(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        sessions.firstOrNull { it.isAutomatic }?.let { latest -> item { RecoveryBundleCard(latest) { onBundle(latest) } } }
        item { OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Archive, null); Spacer(Modifier.width(8.dp)); Text("Create a manual backup") } }
    }
}

@Composable
private fun CockpitTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActivityScreen(history: List<FlashedFile>, journeys: List<FlashJourney>, operation: OperationStatus?, onLogs: (String?, String) -> Unit, onCurrentInstall: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Activity", style = MaterialTheme.typography.headlineLarge) }
        operation?.let { current -> item {
            OperationResultCard(current) { onLogs(current.logPath, current.tail) }
            if (current.kind == "Flash") TextButton(onClick = onCurrentInstall) { Text("Open install details") }
        } }
        if (history.isEmpty()) item { Text("Your kernel installs and their results will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(history) { entry ->
            val journey = journeys.firstOrNull { it.id == entry.jobId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(entry.flashDate, style = MaterialTheme.typography.bodySmall)
                    Text(if (entry.success) journey?.statusLabel ?: "Installer completed · reboot record unavailable" else entry.outputSummary, style = MaterialTheme.typography.bodyMedium)
                    if (entry.logPath != null) TextButton(onClick = { onLogs(entry.logPath, "") }) { Text("View retained log") }
                }
            }
        }
    }
}
