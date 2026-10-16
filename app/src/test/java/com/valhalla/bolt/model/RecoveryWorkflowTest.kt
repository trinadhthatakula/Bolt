package com.valhalla.bolt.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecoveryWorkflowTest {
    private val identity = DeviceIdentity("this-device", "test", "this-build")
    private class FakePartitions : PartitionAccess {
        val bytes = mapOf("boot_a" to "original A".toByteArray(), "boot_b" to "original B".toByteArray())
        var failBackup = false
        var failWrite: String? = null
        val writes = mutableListOf<String>()
        override suspend fun inventory() = bytes.map { Partition(it.key, "/test/${it.key}", it.value.size.toLong()) }
        override suspend fun getActiveSlotSuffix() = "_a"
        override suspend fun backup(partition: Partition, destination: File, onOutput: (String) -> Unit) {
            if (failBackup) throw IOException("read failed")
            destination.writeBytes(bytes.getValue(partition.name))
        }
        override suspend fun restore(partition: Partition, source: File, expectedHash: String, onOutput: (String) -> Unit) {
            writes.add(partition.name)
            if (partition.name == failWrite) throw IOException("write failed")
            assertArrayEquals(bytes.getValue(partition.name), source.readBytes())
        }
    }
    private suspend fun setup(fake: FakePartitions, directory: File): RecoveryManifest = RecoveryWorkflow(fake).snapshot(fake.inventory(), directory, "session", identity, {})
    @Test fun eachSlotUsesItsOwnOriginalImage(): Unit = runBlocking {
        val directory = Files.createTempDirectory("bolt-recovery").toFile()
        val fake = FakePartitions()
        try {
            val manifest = setup(fake, directory)
            val result = RecoveryWorkflow(fake).restore(manifest, "boot_a", true, identity, manifest.images.associate { it.partition to File(directory, "${it.partition}.img") }, {}, {})
            assertTrue(result.isSuccess)
            assertEquals(listOf("boot_a", "boot_b"), fake.writes)
            assertNotEquals(manifest.images[0].sha256, manifest.images[1].sha256)
        } finally { directory.deleteRecursively() }
    }
    @Test fun secondSlotFailureIsRetainedAsPartialFailure(): Unit = runBlocking {
        val directory = Files.createTempDirectory("bolt-recovery").toFile()
        val fake = FakePartitions().apply { failWrite = "boot_b" }
        try {
            val manifest = setup(fake, directory)
            val result = RecoveryWorkflow(fake).restore(manifest, "boot_a", true, identity, manifest.images.associate { it.partition to File(directory, "${it.partition}.img") }, {}, {})
            assertFalse(result.isSuccess)
            assertEquals("Verified", result.outcomes[0].state)
            assertEquals("Failed / possibly partial", result.outcomes[1].state)
        } finally { directory.deleteRecursively() }
    }
    @Test fun tamperedSecondImagePreventsEveryWrite(): Unit = runBlocking {
        val directory = Files.createTempDirectory("bolt-recovery").toFile()
        val fake = FakePartitions()
        try {
            val manifest = setup(fake, directory)
            File(directory, "boot_b.img").writeText("corrupt!!!")
            try {
                RecoveryWorkflow(fake).restore(manifest, "boot_a", true, identity, manifest.images.associate { it.partition to File(directory, "${it.partition}.img") }, {}, {})
                fail("must reject corrupt second source")
            } catch (_: IllegalArgumentException) { assertTrue(fake.writes.isEmpty()) }
        } finally { directory.deleteRecursively() }
    }
    @Test fun wrongDeviceUnknownSlotAndMissingPartnerAreRejected(): Unit = runBlocking {
        val directory = Files.createTempDirectory("bolt-recovery").toFile()
        val fake = FakePartitions()
        try {
            val manifest = setup(fake, directory)
            assertThrows(IllegalArgumentException::class.java) { PartitionPolicy.restorePlan(manifest, "boot_a", false, identity.copy(id = "other"), "_a", listOf(Partition("boot_a", "", 10))) }
            assertThrows(IllegalArgumentException::class.java) { PartitionPolicy.restorePlan(manifest, "boot_a", false, identity, null, listOf(Partition("boot_a", "", 10))) }
            assertThrows(IllegalStateException::class.java) { PartitionPolicy.restorePlan(manifest.copy(images = manifest.images.take(1)), "boot_a", true, identity, "_a", listOf(Partition("boot_a", "", 10))) }
        } finally { directory.deleteRecursively() }
    }
    @Test fun failedBackupNeverLaunchesTheInstaller(): Unit = runBlocking {
        val root = Files.createTempDirectory("bolt-pipeline").toFile()
        val zip = File(root, "kernel.zip")
        ZipOutputStream(zip.outputStream()).use { output -> listOf("anykernel.sh", ArtifactFiles.INSTALLER).forEach {
            output.putNextEntry(ZipEntry(it)); output.write("#!/bin/sh\nexit 0\n".toByteArray()); output.closeEntry()
        } }
        val fake = FakePartitions().apply { failBackup = true }
        var installed = false
        var committed = false
        val executor = object : CommandExecutor {
            override suspend fun execute(script: String, timeoutSeconds: Long, onOutput: (String) -> Unit): CommandResult { installed = true; return CommandResult(0, "") }
        }
        val recovery = File(root, "recovery").apply { mkdir() }
        try {
            try {
                KernelFlashWorkflow(fake, AnyKernelInstaller(executor, "busybox")).flash(zip, ArtifactFiles.sha256(zip), root, recovery, fake.inventory(), "session", identity, { committed = true }, {}, {})
                fail("failed backup must abort")
            } catch (_: IOException) { assertFalse(installed); assertFalse(committed) }
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidPartitionNamesAreNeverTargets() {
        listOf("../boot", "boot;id", "boot a", "\$(id)", "", "boot\n").forEach { assertFalse(PartitionPolicy.validName(it)) }
    }

    @Test fun remappedCanonicalTargetsAreRejectedAndSharedTargetsNeedNoSlot(): Unit = runBlocking {
        val directory = Files.createTempDirectory("bolt-remap").toFile()
        val fake = FakePartitions()
        try {
            val manifest = setup(fake, directory)
            assertThrows(IllegalArgumentException::class.java) { PartitionPolicy.restorePlan(manifest, "boot_a", false, identity, "_a", listOf(Partition("boot_a", "/different/boot_a", manifest.images.first().size))) }
            val image = manifest.images.first().copy(partition = "recovery", slot = null)
            assertEquals(listOf(image), PartitionPolicy.restorePlan(manifest.copy(images = listOf(image)), "recovery", false, identity, null, listOf(Partition("recovery", image.targetPath, image.size))))
        } finally { directory.deleteRecursively() }
    }
    @Test fun firstWriteFailureStopsTheSecondWrite(): Unit = runBlocking {
        val directory = Files.createTempDirectory("bolt-first-failure").toFile()
        val fake = FakePartitions().apply { failWrite = "boot_a" }
        try {
            val manifest = setup(fake, directory)
            val result = RecoveryWorkflow(fake).restore(manifest, "boot_a", true, identity, manifest.images.associate { it.partition to File(directory, "${it.partition}.img") }, {}, {})
            assertFalse(result.isSuccess)
            assertEquals(listOf("boot_a"), fake.writes)
            assertEquals("Not attempted", result.outcomes[1].state)
        } finally { directory.deleteRecursively() }
    }
    @Test fun failedManifestCommitNeverLaunchesTheInstaller(): Unit = runBlocking {
        val root = Files.createTempDirectory("bolt-commit-failure").toFile()
        val zip = File(root, "kernel.zip")
        ZipOutputStream(zip.outputStream()).use { output -> listOf("anykernel.sh", ArtifactFiles.INSTALLER).forEach {
            output.putNextEntry(ZipEntry(it)); output.write("#!/bin/sh\nexit 0\n".toByteArray()); output.closeEntry()
        } }
        val fake = FakePartitions()
        var installed = false
        val executor = object : CommandExecutor {
            override suspend fun execute(script: String, timeoutSeconds: Long, onOutput: (String) -> Unit): CommandResult { installed = true; return CommandResult(0, "") }
        }
        val recovery = File(root, "recovery").apply { mkdir() }
        try {
            try {
                KernelFlashWorkflow(fake, AnyKernelInstaller(executor, "busybox")).flash(zip, ArtifactFiles.sha256(zip), root, recovery, fake.inventory(), "session", identity, { throw IOException("commit failed") }, {}, {})
                fail("failed commit must abort")
            } catch (_: IOException) { assertFalse(installed) }
        } finally { root.deleteRecursively() }
    }
}
