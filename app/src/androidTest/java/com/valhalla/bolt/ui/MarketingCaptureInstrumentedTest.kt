package com.valhalla.bolt.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valhalla.bolt.MainActivity
import com.valhalla.bolt.model.*
import com.valhalla.bolt.ui.theme.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

/** Explicit, emulator-only authoring run. Never starts an installer, restore or reboot. */
class MarketingCaptureInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun captureCurrentCockpit() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("boltMarketingCapture") == "true")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk_gphone"))
        val store = GlobalContext.get().get<ThemePreferencesStore>()
        val directory = File(context.filesDir, "recovery/marketing-sample").apply { mkdirs() }
        val names = listOf("boot_a", "init_boot_a", "vendor_boot_a", "dtbo_a", "vbmeta_a", "recovery_a")
        val manifest = RecoveryManifest("marketing-sample", DeviceIdentity.current(context), System.currentTimeMillis(), names.map { name ->
            val image = File(directory, "$name.img").apply { writeBytes(ByteArray(1024 * 1024)) }
            RecoveryImage(name, image.length(), ArtifactFiles.sha256(image), "_a", "/dev/block/marketing-sample-$name")
        })
        File(directory, "manifest.json").writeText(manifest.encode())
        try {
            compose.runOnIdle { store.setMode(ThemeMode.DARK); store.setAmoled(false); store.setDynamicColors(false); store.setFontPreset(FontPreset.ASGARD) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Root ready").fetchSemanticsNodes().isNotEmpty() }
            capture("01-overview")
            compose.onNodeWithText("Install kernel").performClick()
            compose.onNodeWithText("Select kernel ZIP").performScrollTo().assertIsDisplayed()
            capture("02-install")
            compose.onNodeWithContentDescription("Recovery", useUnmergedTree = true).performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("AutoRecovery · 6 partitions").fetchSemanticsNodes().isNotEmpty() }
            capture("03-recovery")
            compose.onNodeWithText("AutoRecovery · 6 partitions").performClick()
            compose.onNodeWithText("Choose one original image").assertExists()
            capture("04-originals")
            compose.onNodeWithContentDescription("Overview", useUnmergedTree = true).performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Create a manual backup"))
            compose.onNodeWithText("Create a manual backup").performClick()
            compose.onNodeWithText("Keep your originals.").assertExists()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Recommended images").fetchSemanticsNodes().isNotEmpty() }
            capture("05-backup")
            compose.onNodeWithContentDescription("Appearance settings").performClick()
            compose.runOnIdle { store.setMode(ThemeMode.LIGHT) }
            capture("06-appearance-light")
            compose.runOnIdle { store.setMode(ThemeMode.DARK); store.setAmoled(true) }
            capture("07-appearance-amoled")
            compose.onNodeWithContentDescription("Back to Overview").performClick()
            val coordinator = GlobalContext.get().get<OperationCoordinator>()
            check(coordinator.launch("Log preview") { session ->
                session.log("ui_print BOLT · SAMPLE PROVIDER OUTPUT\nui_print\nui_print   /\\   Your kernel, your device.\nui_print  /__\\  Keep a recovery route ready.\nui_print\nui_print Checking package compatibility...\nui_print Preparing original images...\nui_print\nSample log only. No installer or device write ran.\n")
                session.finish(true, "Sample log only; no installer ran")
            })
            compose.waitUntil(10000) { coordinator.status.value?.running == false }
            compose.onNodeWithContentDescription("Activity", useUnmergedTree = true).performClick()
            compose.onNodeWithText("View operation log").performScrollTo().performClick()
            compose.onNodeWithText("Readable log").assertIsSelected()
            capture("08-readable-log")
        } finally {
            directory.deleteRecursively()
            compose.runOnIdle { store.setMode(ThemeMode.SYSTEM); store.setAmoled(false) }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Let transient root-manager toasts disappear from the full-display capture.
        android.os.SystemClock.sleep(if (name in listOf("01-overview", "05-backup")) 8000 else 4000)
        val file = File(context.filesDir, "marketing-captures/$name.png").apply { parentFile!!.mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
