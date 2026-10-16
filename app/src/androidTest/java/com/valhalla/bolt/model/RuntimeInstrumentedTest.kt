package com.valhalla.bolt.model

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RuntimeInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun bundledRuntimeIsVerifiedAndExecutesOnTheEmulator(): Unit = runBlocking {
        val runtime = RootRuntime(context)
        runtime.verify(Shell(false))
        val result = Shell(false).execute("${shellQuote(runtime.busybox)} ash -c 'printf runtime-ok'")
        assertTrue(result.isSuccess)
        assertEquals("runtime-ok", result.output)
    }
    @Test fun rootIsVerifiedByUidNotProcessLaunch(): Unit = runBlocking {
        assertTrue("Grant Bolt root on the test emulator", Shell.isRootAvailable())
        val result = Shell(true).execute("id -u")
        assertTrue(result.isSuccess)
        assertEquals("0", result.output.trim())
    }
    @Test fun harmlessAnyKernelFixturePreservesArgumentsEnvironmentAndFailureExit(): Unit = runBlocking {
        val runtime = RootRuntime(context)
        val root = ArtifactFiles.workspace(File(context.cacheDir, "instrumentation-fixtures"))
        val zip = File(root, "fixture.zip")
        val script = "#!/bin/sh\nprintf 'api=%s fd=%s\\n' \"\$1\" \"\$2\"\nprintf 'AKHOME=%s POSTINSTALL=%s\\n' \"\$AKHOME\" \"\$POSTINSTALL\"\nprintf 'ui_print   Provider message\\n      ui_print\\n' > /proc/self/fd/\"\$2\"\necho diagnostic >&2\nexit 7\n"
        ZipOutputStream(zip.outputStream()).use { output ->
            listOf("anykernel.sh" to "#!/bin/sh\n# fixture, no writes\n", ArtifactFiles.INSTALLER to script).forEach { (name, text) ->
                output.putNextEntry(ZipEntry(name)); output.write(text.toByteArray()); output.closeEntry()
            }
        }
        try {
            var streamed = ""
            val result = AnyKernelInstaller(runtime.locked(Shell(true)), runtime.busybox).install(zip, root) { streamed += it }
            assertEquals(7, result.exitCode)
            assertFalse(result.isSuccess)
            assertTrue(streamed.contains("api=3 fd=1"))
            assertTrue(streamed.contains("AKHOME=${File(root, "tmp").absolutePath}"))
            assertTrue(streamed.contains("POSTINSTALL=${root.absolutePath}"))
            assertTrue(streamed.contains("diagnostic"))
            assertTrue(streamed.contains("ui_print   Provider message"))
            assertTrue(readableOperationLog(streamed).contains("  Provider message\n"))
            assertFalse(readableOperationLog(streamed).contains("      ui_print"))
            assertEquals(script, File(root, "update-binary").readText())
            assertFalse(File(root, "done").exists())
        } finally { Shell(true).execute("rm -rf -- ${shellQuote(root.absolutePath)}") }
    }
    @Test fun manifestRoundTripIsStrictAndBoundToTheDevice() {
        val identity = DeviceIdentity.current(context)
        val manifest = RecoveryManifest("session", identity, 1L, listOf(RecoveryImage("boot_a", 4096, "a".repeat(64), "_a", "/dev/block/vda1")))
        assertEquals(manifest, RecoveryManifest.decode(manifest.encode()))
        assertThrows(IllegalArgumentException::class.java) { RecoveryManifest.decode(manifest.encode().replace("boot_a", "../boot")) }
        assertThrows(IllegalArgumentException::class.java) { RecoveryManifest.decode(manifest.encode().replace("\"complete\": true", "\"complete\": false")) }
    }
    @Test fun readableEmulatorPartitionBackupMatchesSizeWithoutWritingAnyPartition(): Unit = runBlocking {
        val helper = PartitionHelper(Shell(true), RootRuntime(context))
        val inventory = helper.inventory()
        val partition = inventory.firstOrNull { it.name == "vbmeta" && it.sizeBytes <= 16 * 1024 * 1024 }
            ?: error("Test emulator must expose a small vbmeta partition for a read-only backup probe: $inventory")
        val directory = ArtifactFiles.workspace(File(context.cacheDir, "instrumentation-read-only"))
        val image = File(directory, "image").apply { createNewFile() }
        try {
            helper.backup(partition, image)
            assertEquals(partition.sizeBytes, image.length())
            assertEquals(64, ArtifactFiles.sha256(image).length)
        } finally { directory.deleteRecursively() }
    }

    @Test fun successfulInstallerUsesBundledAppletsEvenWithoutSystemPath(): Unit = runBlocking {
        val runtime = RootRuntime(context)
        val root = ArtifactFiles.workspace(File(context.cacheDir, "standalone-fixture"))
        val zip = File(root, "fixture.zip")
        val script = "#!/bin/sh\nPATH=/no-system-tools\nunzip -l \"\$3\" || exit 9\necho success-fixture\nexit 0\n"
        ZipOutputStream(zip.outputStream()).use { output -> listOf("anykernel.sh" to "#!/bin/sh\n", ArtifactFiles.INSTALLER to script).forEach { (name, text) ->
            output.putNextEntry(ZipEntry(name)); output.write(text.toByteArray()); output.closeEntry()
        } }
        try {
            val result = AnyKernelInstaller(runtime.locked(Shell(true)), runtime.busybox).install(zip, root) {}
            assertEquals(result.output, 0, result.exitCode)
            assertTrue(result.output.contains("success-fixture"))
        } finally { Shell(true).execute("rm -rf -- ${shellQuote(root.absolutePath)}") }
    }
    @Test fun nativeLockBlocksRecoveryProbesUntilPreviousWriterExits(): Unit = runBlocking {
        val runtime = RootRuntime(context)
        val marker = File(context.cacheDir, "lock-${java.util.UUID.randomUUID()}")
        val writer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
            runtime.locked(Shell(true)).execute("${shellQuote(runtime.busybox)} touch ${shellQuote(marker.path)}; ${shellQuote(runtime.busybox)} sleep 2")
        }
        try {
            kotlinx.coroutines.withTimeout(10000) { while (!marker.exists()) kotlinx.coroutines.delay(20) }
            try { runtime.ensureIdle(Shell(true)); fail("native writer must block probes") } catch (_: java.io.IOException) { }
            assertTrue(writer.await().isSuccess)
            runtime.ensureIdle(Shell(true))
        } finally { writer.await(); marker.delete() }
    }
}
