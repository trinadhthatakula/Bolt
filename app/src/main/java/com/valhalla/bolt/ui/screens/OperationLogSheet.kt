package com.valhalla.bolt.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.valhalla.bolt.ui.theme.LocalTechnicalFontFamily
import com.valhalla.bolt.model.readableOperationLog
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
    var rawMode by rememberSaveable(path) { mutableStateOf(false) }
    var retained by remember(path) { mutableStateOf("") }
    LaunchedEffect(path, tail) {
        if (tail.isEmpty() && path != null) retained = withContext(Dispatchers.IO) {
            runCatching {
                java.io.RandomAccessFile(path, "r").use { file ->
                    val length = file.length()
                    file.seek((length - 32768).coerceAtLeast(0))
                    ByteArray((length - file.filePointer).toInt()).also { file.readFully(it) }.toString(Charsets.UTF_8)
                }
            }.getOrDefault("")
        }
    }
    // The full accumulated tail is reformatted on every update, including split pipe reads.
    val source = tail.ifEmpty { retained }
    val displayed = remember(source, rawMode) { if (rawMode) source else readableOperationLog(source) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !rawMode, onClick = { rawMode = false }, label = { Text("Readable log") })
                FilterChip(selected = rawMode, onClick = { rawMode = true }, label = { Text("Raw log") })
            }
            TextButton(onClick = { export.launch("Bolt-operation.log") }, enabled = path != null && File(path).isFile) { Text("Export full raw log") }
            message?.let { Text(it) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                item { SelectionContainer { Text(displayed.ifEmpty { "No output yet. Full retained output can be exported above." }, fontFamily = LocalTechnicalFontFamily.current, style = MaterialTheme.typography.bodySmall) } }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
