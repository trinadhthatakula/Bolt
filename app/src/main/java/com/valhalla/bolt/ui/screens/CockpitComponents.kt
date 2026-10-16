package com.valhalla.bolt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valhalla.bolt.model.BackupSession
import com.valhalla.bolt.model.OperationStatus
import com.valhalla.bolt.ui.theme.LocalTechnicalFontFamily
import java.text.DateFormat
import java.util.Date

@Composable
fun PartitionBadges(names: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { name -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
            Text(name, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontFamily = LocalTechnicalFontFamily.current)
        } }
    }
}

@Composable
fun RecoveryBundleCard(session: BackupSession, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(session.displayTitle, style = MaterialTheme.typography.titleMedium)
            Text(session.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PartitionBadges(session.files.map { it.partitionName }.take(6))
            if (session.files.size > 6) Text("+${session.files.size - 6} partitions", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun OperationResultCard(operation: OperationStatus, onLogs: () -> Unit) {
    val error = operation.uncertain || operation.phase != "Completed"
    Card(colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when {
                operation.uncertain -> "Changes may be partial"
                operation.phase == "Completed" -> "${operation.kind} completed"
                operation.phase == "Failed" -> "No partitions changed"
                else -> "Review operation outcome"
            }, style = MaterialTheme.typography.titleLarge)
            Text(if (operation.kind == "Flash" && operation.phase == "Completed") "Installer completed · exit 0. Restart and feedback are recorded in install details." else operation.message, style = MaterialTheme.typography.bodyMedium)
            operation.outcomes.forEach { target ->
                Text("${target.partition} · ${target.state}", style = MaterialTheme.typography.labelMedium)
                target.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onLogs) { Text("View operation log") }
        }
    }
}

fun readableDate(time: Long): String = if (time > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time)) else "Date unavailable"
fun readableSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f GiB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MiB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> "${bytes / 1024} KiB"
    else -> "$bytes bytes"
}
