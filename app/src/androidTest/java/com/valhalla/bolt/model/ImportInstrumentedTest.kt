package com.valhalla.bolt.model

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valhalla.bolt.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class ImportInstrumentedTest {
    @get:Rule val compose = AndroidComposeTestRule(
        ActivityScenarioRule<MainActivity>(Intent(Intent.ACTION_VIEW).apply {
            val target = InstrumentationRegistry.getInstrumentation().targetContext
            component = ComponentName(target.packageName, MainActivity::class.java.name)
            setDataAndType(Uri.parse("content://com.valhalla.bolt.test.fixtures/valid"), "application/zip")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    ) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        requireNotNull(activity)
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun uri(name: String) = Uri.parse("content://com.valhalla.bolt.test.fixtures/$name")
    @Test fun hostileNamesNullStreamsAndBoundedImportsAreHandled() {
        val documents = DocumentHandler(context)
        val file = documents.stage(uri("valid"))
        try {
            assertEquals("artifact", file.name)
            assertEquals(context.cacheDir.canonicalPath, file.parentFile!!.parentFile!!.parentFile!!.canonicalPath)
            ArtifactFiles.validateAnyKernel(file)
            assertTrue(documents.displayName(uri("valid")).contains("../"))
        } finally { file.parentFile!!.deleteRecursively() }
        assertThrows(IllegalArgumentException::class.java) { documents.stage(uri("null")) }
        assertThrows(java.io.IOException::class.java) { documents.stage(uri("oversize"), 100) }
    }
    @Test fun externalImportSurvivesRecreationAndRequiresSeparateReview() {
        val coordinator = GlobalContext.get().get<OperationCoordinator>()
        coordinator.acknowledgeInterruption()
        val previous = coordinator.status.value?.id
        compose.waitUntil(15000) { compose.onAllNodesWithText("Import AnyKernel ZIP?").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Import AnyKernel ZIP?").assertIsDisplayed()
        compose.onNodeWithText("Import ZIP").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Change ZIP").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Change ZIP").assertIsDisplayed()
        compose.onNodeWithText("Review and flash").assertIsDisplayed()
        assertEquals(previous, coordinator.status.value?.id)
        compose.onNodeWithText("Review and flash").performClick()
        // This emulator has vbmeta only. Production must refuse an unsafe recovery bypass.
        compose.waitUntil(15000) { compose.onAllNodesWithText("No boot, init_boot or recovery target was discovered; flashing is blocked without kernel recovery").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New job").performScrollTo().performClick()
        compose.onNodeWithText("Select AnyKernel ZIP").assertIsDisplayed()
        assertEquals(previous, coordinator.status.value?.id)
    }

    @Test fun restoreConfirmationIsConsumedWhenPreflightRejectsTheTarget() {
        compose.onNodeWithText("Cancel").performClick()
        val directory = java.io.File(context.filesDir, "recovery/ui-fixture-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val file = java.io.File(directory, "recovery.img").apply { writeText("harmless fixture") }
        val manifest = RecoveryManifest("ui-fixture", DeviceIdentity.current(context), 1L, listOf(RecoveryImage("recovery", file.length(), ArtifactFiles.sha256(file), null, "/dev/block/nonexistent-ui-fixture")))
        java.io.File(directory, "manifest.json").writeText(manifest.encode())
        try {
            compose.onNodeWithText("Restore").performClick()
            compose.onNodeWithText("Refresh").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Restore recovery").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Restore recovery").performScrollTo().performClick()
            compose.onNodeWithText("Confirm verified restore").assertIsDisplayed()
            compose.onAllNodesWithText("Restore").filterToOne(hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Confirm verified restore").fetchSemanticsNodes().isEmpty() }
            val coordinator = GlobalContext.get().get<OperationCoordinator>()
            compose.waitUntil(15000) { coordinator.status.value?.running == false }
            assertFalse(coordinator.status.value!!.uncertain)
            assertEquals("Failed", coordinator.status.value!!.phase)
            assertTrue(coordinator.status.value!!.message.contains("live partition inventory"))
        } finally { directory.deleteRecursively() }
    }
}
