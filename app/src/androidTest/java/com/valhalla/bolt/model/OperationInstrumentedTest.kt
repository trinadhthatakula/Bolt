package com.valhalla.bolt.model

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.valhalla.bolt.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

class OperationInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun operationSurvivesActivityRecreationAndRejectsAnotherJob(): Unit = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val coordinator = GlobalContext.get().get<OperationCoordinator>()
            coordinator.acknowledgeInterruption()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            assertTrue(coordinator.launch("Test harmless operation") { session ->
                session.phase("Waiting", "Harmless lifecycle fixture")
                session.log("retained lifecycle diagnostic\n")
                entered.complete(Unit)
                release.await()
                session.target(TargetOutcome("fixture", "Verified"))
                session.exitCode(0)
            })
            try {
                withTimeout(20000) { entered.await() }
                assertFalse(coordinator.launch("Second job") { error("Must never run") })
                activity.recreate()
                assertTrue(coordinator.status.value!!.running)
            } finally { release.complete(Unit) }
            withTimeout(20000) { while (coordinator.isBlocked) delay(50) }
            assertEquals("Completed", coordinator.status.value!!.phase)
            val reopened = OperationCoordinator(context)
            assertEquals(listOf(TargetOutcome("fixture", "Verified")), reopened.status.value!!.outcomes)
            assertEquals(0, reopened.status.value!!.exitCode)
            assertTrue(reopened.status.value!!.tail.contains("retained lifecycle diagnostic"))
        }
    }
    @Test fun failureAfterMutationRequiresAcknowledgmentAndIsNotReplayed(): Unit = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val coordinator = GlobalContext.get().get<OperationCoordinator>()
            coordinator.acknowledgeInterruption()
            assertTrue(coordinator.launch("Test partial failure") { session ->
                session.mutating() // Status simulation only. No partition writes.
                session.target(TargetOutcome("fixture", "Failed / possibly partial", "fixture failure"))
                throw java.io.IOException("simulated write failure")
            })
            withTimeout(20000) { while (coordinator.status.value?.running == true) delay(50) }
            assertTrue(coordinator.isBlocked)
            val reopened = OperationCoordinator(context)
            assertTrue(reopened.status.value!!.uncertain)
            assertFalse(reopened.launch("Replay") { error("Must never run") })
            withTimeout(20000) { while (coordinator.status.value?.running == true) delay(50) }
            delay(100)
            coordinator.acknowledgeInterruption()
            assertFalse(coordinator.isBlocked)
        }
    }
    @Test fun runningAndMalformedJournalsFailClosed() {
        val directory = ArtifactFiles.workspace(File(context.cacheDir, "journal-tests"))
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        try {
            File(directory, "operation.json").writeText("broken json")
            assertTrue(OperationCoordinator(isolated).isBlocked)
            File(directory, "operation.json").writeText("""{"id":"test","kind":"Flash","phase":"Executing","running":true,"message":"old","logPath":"","uncertain":false}""")
            val reopened = OperationCoordinator(isolated)
            assertTrue(reopened.isBlocked)
            assertEquals("Unknown", reopened.status.value!!.phase)
            reopened.acknowledgeInterruption()
            assertFalse(reopened.isBlocked)
        } finally { directory.deleteRecursively() }
    }

    @Test fun journalWriteFailureBlocksExecutionAndCanBeRecovered() {
        val directory = ArtifactFiles.workspace(File(context.cacheDir, "journal-failure-tests"))
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        val coordinator = OperationCoordinator(isolated)
        val journalDirectory = File(directory, "operation.json.new").apply { mkdir() }
        try {
            assertFalse(coordinator.launch("Must not execute") { error("No durable operation record") })
            assertTrue(coordinator.isBlocked)
            assertFalse(coordinator.status.value!!.running)
            assertTrue(journalDirectory.delete())
            coordinator.acknowledgeInterruption()
            assertFalse(coordinator.isBlocked)
        } finally { directory.deleteRecursively() }
    }

    @Test fun exportedAliasImportsWithoutLaunchingAnOperation() {
        val coordinator = GlobalContext.get().get<OperationCoordinator>()
        val previous = coordinator.status.value?.id
        val uri = android.net.Uri.parse("content://com.valhalla.bolt.test.fixtures/alias")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            component = android.content.ComponentName(context.packageName, "${context.packageName}.FlashAnyKernel")
            setDataAndType(uri, "application/zip")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { assertEquals(uri, it.intent.data) }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun hasImport(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.text?.toString() == "Import AnyKernel ZIP?") return true
                return (0 until node.childCount).any { hasImport(node.getChild(it)) }
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            while (!hasImport(automation.rootInActiveWindow) && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(25)
            assertTrue(hasImport(automation.rootInActiveWindow))
            assertEquals(previous, coordinator.status.value?.id)
        }
    }
}
