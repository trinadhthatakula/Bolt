package com.valhalla.bolt.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.valhalla.bolt.MainActivity
import com.valhalla.bolt.model.*
import com.valhalla.bolt.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.koin.core.context.GlobalContext
import java.io.File

class AppearanceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun appearanceSettingsApplyAndSurviveRecreation() {
        val store = GlobalContext.get().get<ThemePreferencesStore>()
        compose.onNodeWithContentDescription("Appearance settings").performClick()
        compose.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        compose.waitUntil { store.state.value.mode == ThemeMode.DARK }
        compose.onNodeWithContentDescription("AMOLED mode").performClick()
        compose.onNodeWithContentDescription("Dynamic colors").performClick()
        compose.waitUntil { store.state.value.amoled && store.state.value.dynamicColors }
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("System font"))
        compose.onNodeWithContentDescription("System font").performClick()
        compose.waitUntil { store.state.value.fontPreset == FontPreset.SYSTEM }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, store.state.value.mode)
        assertEquals(FontPreset.SYSTEM, ThemePreferencesStore(context).state.value.fontPreset)
        assertTrue(ThemePreferencesStore(context).state.value.amoled)
        assertTrue(ThemePreferencesStore(context).state.value.dynamicColors)
        compose.onNodeWithText("AMOLED mode").assertExists()
        compose.onNodeWithText("Dynamic colors").assertExists()
        compose.runOnIdle { store.setFontPreset(FontPreset.ASGARD); store.setMode(ThemeMode.SYSTEM); store.setAmoled(false); store.setDynamicColors(false) }
    }

    @Test fun allOutfitWeightsAndStylesLoadWithExplicitNativeWeights() {
        val fonts = (bodyFontFamily as FontListFontFamily).fonts
        assertEquals(18, fonts.size)
        for (weight in listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)) {
            for (style in listOf(FontStyle.Normal, FontStyle.Italic)) {
                val font = fonts.single { it.weight.weight == weight && it.style == style } as AndroidFont
                val typeface = font.typefaceLoader.loadBlocking(context, font)!!
                assertEquals("Requested $weight / $style lost", weight, typeface.weight)
                assertFalse(typeface.isItalic) // Outfit has no italic axis; Thor uses upright aliases.
            }
        }
        val regular = fonts.first { it.weight == FontWeight.Normal && it.style == FontStyle.Normal } as AndroidFont
        val bold = fonts.first { it.weight == FontWeight.Bold && it.style == FontStyle.Normal } as AndroidFont
        assertTrue("Native outlines must visibly change with weight", ink(bold.typefaceLoader.loadBlocking(context, bold)!!) > ink(regular.typefaceLoader.loadBlocking(context, regular)!!))
        val typography = typographyFor(FontPreset.ASGARD)
        assertEquals(FontWeight.SemiBold, typography.titleLarge.fontWeight)
        assertEquals(FontWeight.Black, typography.displayLarge.fontWeight)
        assertEquals(bodyFontFamily, typography.bodyLarge.fontFamily)
        assertEquals(firaMonoFontFamily, typography.labelLarge.fontFamily)
        assertEquals(bodyFontFamily, typography.titleLargeEmphasized.fontFamily)
    }

    @Test fun recoveryLibraryShowsOneBundleAndOpensPartitionDetails() {
        val directory = File(context.filesDir, "recovery/ui-bundle-fixture").apply { mkdirs() }
        val names = listOf("boot_a", "init_boot_a", "vendor_boot_a", "dtbo_a", "vbmeta_a", "recovery_a")
        val manifest = RecoveryManifest("ui-bundle-fixture", DeviceIdentity.current(context), System.currentTimeMillis(), names.map { name ->
            val image = File(directory, "$name.img").apply { writeBytes(ByteArray(128) { 1 }) }
            RecoveryImage(name, image.length(), ArtifactFiles.sha256(image), "_a", "/dev/block/ui-fixture-$name")
        })
        File(directory, "manifest.json").writeText(manifest.encode())
        try {
            compose.activityRule.scenario.recreate()
            compose.onNodeWithContentDescription("Recovery", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("AutoRecovery · 6 partitions").fetchSemanticsNodes().isNotEmpty() }
            capture(File(context.filesDir, "ui-validation/recovery-asgard-bundle.png"))
            compose.onNodeWithText("AutoRecovery · 6 partitions").performScrollTo().performClick()
            compose.onNodeWithText("Choose one original image").assertExists()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Review restore of boot_a"))
            compose.onNodeWithText("Review restore of boot_a").assertExists()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("All recovery bundles"))
            compose.onNodeWithText("All recovery bundles").performClick()
            compose.onNodeWithText("Your recovery library.").assertExists()
        } finally { directory.deleteRecursively() }
    }

    @Test fun rebootObservationAndFeedbackReplaceThePendingRebootAction() {
        val coordinator = GlobalContext.get().get<OperationCoordinator>()
        val journeys = GlobalContext.get().get<FlashJourneyStore>()
        val journal = File(context.filesDir, "flash-journeys.json")
        val original = journal.takeIf { it.exists() }?.readBytes()
        coordinator.acknowledgeInterruption()
        try {
            assertTrue(coordinator.launch("Flash") { session ->
                journeys.begin(session.value.id, "BakaSU UI fixture", DeviceIdentity.current(context).id, "boot-before-fixture")
                session.exitCode(0)
                session.finish(true, "Harmless UI result fixture; no installer ran")
                journeys.complete(session.value.id)
            })
            compose.waitUntil(10000) { !coordinator.isBlocked && journeys.state.value.firstOrNull()?.completedAt != null }
            journeys.observeLatest(DeviceIdentity.current(context).id, "boot-after-fixture")
            compose.onNodeWithText("Device restarted").performScrollTo().performClick()
            capture(File(context.filesDir, "ui-validation/install-asgard-restarted.png"))
            compose.onNodeWithText("Working well").performScrollTo().performClick()
            compose.waitUntil(5000) { journeys.state.value.firstOrNull()?.feedback == "working" }
            compose.onNodeWithText("You confirmed it’s working").assertExists()
            compose.onNodeWithText("Reboot device").assertDoesNotExist()
            assertNotNull(FlashJourneyStore(context).state.value.first().bootObservedAt)
            assertEquals("working", FlashJourneyStore(context).state.value.first().feedback)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Install another kernel"))
            compose.onNodeWithText("Install another kernel").performClick()
            compose.onNodeWithText("Select kernel ZIP").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Activity", useUnmergedTree = true).performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Open install details"))
            compose.onNodeWithText("Open install details").performClick()
            compose.onNodeWithText("You confirmed it’s working").assertExists()
        } finally {
            if (original == null) journal.delete() else journal.writeBytes(original)
            coordinator.launch("UI validation complete") { it.finish(true, "No device writes or reboot performed") }
        }
    }

    @Test fun captureCockpitAndAppearanceInAsgardDarkLightAndAmoled() {
        val store = GlobalContext.get().get<ThemePreferencesStore>()
        val directory = File(context.filesDir, "ui-validation").apply { mkdirs() }
        try {
            compose.runOnIdle { store.setMode(ThemeMode.DARK); store.setAmoled(false); store.setDynamicColors(false); store.setFontPreset(FontPreset.ASGARD) }
            compose.waitForIdle()
            capture(File(directory, "cockpit-asgard-dark.png"))
            compose.onNodeWithContentDescription("Appearance settings").performClick()
            compose.runOnIdle { store.setMode(ThemeMode.LIGHT) }
            compose.waitForIdle()
            capture(File(directory, "appearance-asgard-light.png"))
            compose.runOnIdle { store.setMode(ThemeMode.DARK); store.setAmoled(true) }
            compose.waitForIdle()
            capture(File(directory, "appearance-asgard-amoled.png"))
        } finally {
            compose.runOnIdle { store.setMode(ThemeMode.SYSTEM); store.setAmoled(false); store.setDynamicColors(false); store.setFontPreset(FontPreset.ASGARD) }
        }
    }

    private fun capture(file: File) {
        file.parentFile!!.mkdirs()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun ink(typeface: Typeface): Int {
        val bitmap = Bitmap.createBitmap(800, 140, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 64f; color = android.graphics.Color.WHITE }
        Canvas(bitmap).drawText("Bolt Cockpit 012345", 10f, 90f, paint)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val count = pixels.count { android.graphics.Color.alpha(it) > 127 }
        bitmap.recycle()
        return count
    }
}
