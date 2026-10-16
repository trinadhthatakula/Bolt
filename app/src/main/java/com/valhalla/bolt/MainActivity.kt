package com.valhalla.bolt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.valhalla.bolt.ui.screens.HomeScreen
import com.valhalla.bolt.ui.theme.BoltTheme

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)
    private var incomingError by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent) else {
            incomingUri = savedInstanceState.getString("incomingUri")?.let(Uri::parse)
            incomingError = savedInstanceState.getString("incomingError")
        }
        setContent { BoltTheme {
            HomeScreen(incomingUri = incomingUri, onImportConsumed = { incomingUri = null })
            incomingError?.let { message -> AlertDialog(
                onDismissRequest = { incomingError = null }, title = { Text("ZIP import unavailable") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = { incomingError = null }) { Text("Close") } }
            ) }
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("incomingUri", incomingUri?.toString())
        outState.putString("incomingError", incomingError)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    @Suppress("DEPRECATION")
    private fun receive(intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) return
        if ((intent.clipData?.itemCount ?: 0) > 1) { incomingError = "Select one AnyKernel ZIP at a time"; return }
        val uri = if (intent.action == Intent.ACTION_VIEW) intent.data else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri?.scheme != "content") { incomingError = "A readable content document is required"; return }
        incomingUri = uri
    }
}
