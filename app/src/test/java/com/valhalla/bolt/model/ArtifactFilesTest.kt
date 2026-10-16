package com.valhalla.bolt.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArtifactFilesTest {
    private fun zip(vararg names: String): File {
        val file = Files.createTempFile("bolt-fixture", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { output -> names.forEach { name ->
            output.putNextEntry(ZipEntry(name)); output.write("#!/bin/sh\nexit 0\n".toByteArray()); output.closeEntry()
        } }
        return file
    }
    @Test fun markerOnlyAndInstallerOnlyAreRejected() {
        listOf(zip("anykernel.sh"), zip(ArtifactFiles.INSTALLER), zip("ANYKERNEL.SH", ArtifactFiles.INSTALLER), zip("anykernel.sh", "nested/${ArtifactFiles.INSTALLER}")).forEach { file ->
            try { assertThrows(IllegalArgumentException::class.java) { ArtifactFiles.validateAnyKernel(file) } } finally { file.delete() }
        }
    }
    @Test fun traversalEntriesAreRejectedBeforeExecution() {
        val file = zip("anykernel.sh", ArtifactFiles.INSTALLER, "../outside")
        try { assertThrows(IllegalArgumentException::class.java) { ArtifactFiles.validateAnyKernel(file) } } finally { file.delete() }
    }
    @Test fun sizeLimitStopsAnUntrustedStream() {
        val output = ByteArrayOutputStream()
        assertThrows(java.io.IOException::class.java) { ArtifactFiles.copyBounded(ByteArrayInputStream(ByteArray(1024)), output, 100) }
        assertEquals(0, output.size())
    }
    @Test fun installerIsUnmodifiedAndExitIsPreserved(): Unit = runBlocking {
        val file = zip("anykernel.sh", ArtifactFiles.INSTALLER)
        val directory = Files.createTempDirectory("bolt-work").toFile()
        var command = ""
        val executor = object : CommandExecutor {
            override suspend fun execute(script: String, timeoutSeconds: Long, onOutput: (String) -> Unit): CommandResult {
                command = script
                return CommandResult(7, "installer failure")
            }
        }
        try {
            val result = AnyKernelInstaller(executor, "/tools/busybox").install(file, directory) {}
            assertEquals(7, result.exitCode)
            assertFalse(result.isSuccess)
            assertEquals("#!/bin/sh\nexit 0\n", File(directory, "update-binary").readText())
            assertTrue(command.contains("AKHOME="))
            assertTrue(command.contains("POSTINSTALL="))
            assertTrue(command.contains(" ash "))
            assertTrue(command.contains(" 3 1 "))
            assertFalse(command.contains("touch "))
            assertFalse(command.contains("sed "))
        } finally { file.delete(); directory.deleteRecursively() }
    }

    @Test fun symlinkModesAndDuplicateEntriesAreRejected() {
        val symlink = zip("anykernel.sh", ArtifactFiles.INSTALLER)
        val duplicate = zip("anykernel.sh", ArtifactFiles.INSTALLER, "otherfile.sh")
        try {
            val bytes = symlink.readBytes()
            val central = bytes.indices.first { index -> index + 46 < bytes.size && bytes.copyOfRange(index, index + 4).contentEquals(byteArrayOf(0x50, 0x4b, 0x01, 0x02)) }
            bytes[central + 41] = 0xa0.toByte() // Unix symlink file type.
            symlink.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { ArtifactFiles.validateAnyKernel(symlink) }
            val duplicates = duplicate.readBytes().toString(Charsets.ISO_8859_1).replace("otherfile.sh", "anykernel.sh").toByteArray(Charsets.ISO_8859_1)
            duplicate.writeBytes(duplicates)
            assertThrows(IllegalArgumentException::class.java) { ArtifactFiles.validateAnyKernel(duplicate) }
        } finally { symlink.delete(); duplicate.delete() }
    }
    @Test fun exportedHashReadingRejectsOverlongStreams() {
        assertThrows(IllegalArgumentException::class.java) { ArtifactFiles.sha256(ByteArrayInputStream(ByteArray(101)), 100) }
    }
}
