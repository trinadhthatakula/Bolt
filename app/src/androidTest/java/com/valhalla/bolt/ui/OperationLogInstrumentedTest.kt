package com.valhalla.bolt.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valhalla.bolt.ui.screens.OperationLogSheet
import com.valhalla.bolt.ui.theme.BoltTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class OperationLogInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun streamedProviderMessagesAreReadableAndRawOutputRemainsAvailable() {
        val raw = "ui_print   Provider artwork\n      ui_print\nui_print Thank you for using this kernel!\n      ui_print\nInstaller exit: 7\n"
        val readable = "  Provider artwork\nThank you for using this kernel!\nInstaller exit: 7\n"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("log-ui-fixture-", ".log", context.cacheDir).apply { writeText(raw) }
        val output = mutableStateOf("ui_pr")
        try {
            compose.setContent { BoltTheme { OperationLogSheet(file.path, output.value) {} } }
            compose.runOnIdle { output.value += raw.removePrefix("ui_pr") }
            compose.onNodeWithText("Readable log").assertIsSelected()
            compose.onNodeWithText(readable).assertIsDisplayed()
            compose.onNodeWithText("Raw log").performClick()
            compose.onNodeWithText(raw).assertIsDisplayed()
            compose.onNodeWithText("Readable log").performClick()
            compose.onNodeWithText(readable).assertIsDisplayed()
            compose.onNodeWithText("Export full raw log").assertIsEnabled()
            assertEquals(raw, file.readText())
        } finally { file.delete() }
    }

    @Test fun historicLogsLoadABoundedRetainedTailInBothModes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = "ui_print Historic provider message\nui_print\nInstaller exit: 0\n"
        val file = File.createTempFile("historic-log-", ".log", context.cacheDir)
        val original = "OLD_PREFIX\n" + "padding\n".repeat(6000) + raw
        file.writeText(original)
        try {
            compose.setContent { BoltTheme { OperationLogSheet(file.path, "") {} } }
            compose.waitUntil(5000) { compose.onAllNodes(hasText("Historic provider message", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Historic provider message", substring = true)).assertIsDisplayed()
            compose.onNode(hasText("OLD_PREFIX", substring = true)).assertDoesNotExist()
            compose.onNodeWithText("Raw log").performClick()
            compose.onNode(hasText(raw, substring = true)).assertIsDisplayed()
            compose.onNodeWithText("Readable log").performClick()
            compose.onNode(hasText("ui_print Historic", substring = true)).assertDoesNotExist()
            assertEquals(original, file.readText())
        } finally { file.delete() }
    }
}
