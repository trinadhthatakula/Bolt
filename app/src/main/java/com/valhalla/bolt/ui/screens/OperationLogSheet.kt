package com.valhalla.bolt.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationLogSheet(path: String?, tail: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && path != null) scope.launch {
            message = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri, "wt").use { output ->
                    requireNotNull(output) { "Cannot open log destination" }
                    File(path).inputStream().use { it.copyTo(output) }
                }
                "Log exported"
            }.getOrElse { "Log export failed: ${it.message}" } }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Operation log", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { export.launch("Bolt-operation.log") }, enabled = path != null && File(path).isFile) { Text("Export full log") }
            message?.let { Text(it) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                item { Text(tail.ifEmpty { "No output yet. Full retained output can be exported above." }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
