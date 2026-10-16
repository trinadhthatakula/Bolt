package com.valhalla.bolt.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OperationLogFormatterTest {
    @Test fun providerMessagesAndArtworkKeepTheirSpacingWithoutFlushMarkers() {
        val raw = "ui_print   /\\\n      ui_print\nui_print  /  \\\n      ui_print\nui_print Done!\nui_print\n"
        assertEquals("  /\\\n /  \\\nDone!\n", readableOperationLog(raw))
    }

    @Test fun diagnosticsAndLiteralUiPrintReferencesArePreserved() {
        val raw = "ash: bad number\nInstaller exit: 7\nui_printed=literal\nDiagnostic mentions ui_print hello\n"
        assertEquals(raw, readableOperationLog(raw))
        assertEquals("The ui_print command prints messages\n", readableOperationLog("ui_print The ui_print command prints messages\n"))
    }

    @Test fun explicitBlankMessagesAndTabsRemainVisible() {
        assertEquals("\n\tIndented\n", readableOperationLog("ui_print \n ui_print\n\tui_print\t\tIndented\n ui_print\n"))
    }

    @Test fun crlfAndAnUnterminatedMessageWorkAcrossAccumulatedTailUpdates() {
        val chunks = listOf("ui_pr", "int Hello\r", "\n   ui_", "print\r\nui_print Bye")
        var accumulated = ""
        for (chunk in chunks) accumulated += chunk
        assertEquals("Hello\nBye", readableOperationLog(accumulated))
        assertEquals("Hello", readableOperationLog("ui_print Hello"))
        assertEquals("", readableOperationLog("ui_print"))
    }
}
