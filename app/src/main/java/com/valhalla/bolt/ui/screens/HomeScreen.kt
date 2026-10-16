package com.valhalla.bolt.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.model.OperationCoordinator
import com.valhalla.bolt.model.navItems
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun HomeScreen(modifier: Modifier = Modifier, incomingUri: Uri? = null, onImportConsumed: () -> Unit = {}) {
    val pager = rememberPagerState { navItems.size }
    val scope = rememberCoroutineScope()
    val operations: OperationCoordinator = koinInject()
    val status by operations.status.collectAsStateWithLifecycle()
    var logs by remember { mutableStateOf(false) }
    var acknowledge by remember { mutableStateOf(false) }
    BackHandler(enabled = status?.running == true) { logs = true }
    LaunchedEffect(incomingUri) { if (incomingUri != null) pager.animateScrollToPage(0) }
    Scaffold(modifier.fillMaxSize(), bottomBar = {
        NavigationBar {
            navItems.forEachIndexed { index, item -> NavigationBarItem(
                selected = pager.currentPage == index,
                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                icon = { Icon(painterResource(item.icon), item.title) }, label = { Text(item.title) }
            ) }
        }
    }) { padding ->
        Column(Modifier.padding(padding)) {
            status?.let { operation -> Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${operation.kind}: ${operation.phase}", style = MaterialTheme.typography.titleSmall)
                    Text(operation.message, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    if (operation.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { logs = true }) { Text("Operation log") }
                        if (operation.uncertain) TextButton(onClick = { acknowledge = true }) { Text("Review unknown outcome") }
                    }
                }
            } }
            HorizontalPager(pager, Modifier.weight(1f)) { page -> when (page) {
                0 -> FlashScreen(incomingUri = incomingUri, onImportConsumed = onImportConsumed)
                1 -> BackUpScreen(Modifier.fillMaxSize())
                2 -> RestoreScreen(Modifier.fillMaxSize())
            } }
        }
    }
    if (logs) OperationLogSheet(status?.logPath, status?.tail.orEmpty()) { logs = false }
    if (acknowledge) AlertDialog(
        onDismissRequest = { acknowledge = false }, title = { Text("Operation outcome is unknown") },
        text = { Text("Review the retained log and recovery images, and check whether the native installer is still running. Acknowledging does not verify or undo partition changes. Bolt will not replay the interrupted operation.") },
        confirmButton = { TextButton(onClick = { acknowledge = false; operations.acknowledgeInterruption() }) { Text("I checked the recovery state") } },
        dismissButton = { TextButton(onClick = { acknowledge = false }) { Text("Keep operations blocked") } }
    )
}
