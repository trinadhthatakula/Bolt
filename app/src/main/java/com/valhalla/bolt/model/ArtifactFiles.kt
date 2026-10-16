package com.valhalla.bolt.model

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

object ArtifactFiles {
    const val MAX_ZIP_BYTES = 512L * 1024 * 1024
    const val MAX_INSTALLER_BYTES = 2L * 1024 * 1024
    const val INSTALLER = "META-INF/com/google/android/update-binary"
    fun workspace(parent: File): File {
        check(parent.exists() || parent.mkdirs()) { "Cannot create staging directory" }
        return File(parent, UUID.randomUUID().toString()).also { check(it.mkdir()) { "Cannot create job directory" } }
    }
    fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        var total = 0L
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("File exceeds the supported size limit")
            output.write(buffer, 0, count)
        }
        return total
    }
    fun sha256(file: File): String = file.inputStream().use { sha256(it) }
    fun sha256(input: InputStream, limit: Long = Long.MAX_VALUE): String {
        var total = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Stream exceeds expected size" }
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun validateAnyKernel(file: File): Long {
        require(file.length() in 1..MAX_ZIP_BYTES) { "ZIP is empty or too large" }
        rejectSpecialEntries(file)
        return ZipFile(file).use { zip ->
            val names = HashSet<String>()
            var count = 0
            var expanded = 0L
            for (entry in zip.entries()) {
                require(++count <= 10000) { "ZIP contains too many entries" }
                require(names.add(entry.name)) { "ZIP contains duplicate entries" }
                require(!entry.name.startsWith('/') && '\\' !in entry.name && '\u0000' !in entry.name && entry.name.split('/').none { it == ".." || it == "." }) { "Unsafe ZIP entry path" }
                require(entry.size >= 0) { "ZIP entry size is unknown" }
                expanded += entry.size
                require(expanded <= 2L * 1024 * 1024 * 1024) { "ZIP expanded size exceeds the supported limit" }
            }
            require("anykernel.sh" in names && !zip.getEntry("anykernel.sh").isDirectory && zip.getEntry("anykernel.sh").size > 0 && INSTALLER in names) { "Select an AnyKernel ZIP with root anykernel.sh and canonical update-binary" }
            val installer = zip.getEntry(INSTALLER)
            require(!installer.isDirectory && installer.size in 1..MAX_INSTALLER_BYTES) { "Installer is empty or too large" }
            zip.getInputStream(installer).use { input ->
                require(input.read() == 35 && input.read() == 33) { "Only script-based AnyKernel installers are supported" }
            }
            expanded
        }
    }
    private fun rejectSpecialEntries(file: File) {
        RandomAccessFile(file, "r").use { input ->
            val tailSize = minOf(file.length(), 65557).toInt()
            require(tailSize >= 22) { "Invalid ZIP directory" }
            val tail = ByteArray(tailSize)
            input.seek(file.length() - tailSize); input.readFully(tail)
            fun u16(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
            fun u32(bytes: ByteArray, offset: Int): Long = (0..3).sumOf { (bytes[offset + it].toLong() and 255) shl (8 * it) }
            val end = (tailSize - 22 downTo 0).firstOrNull { u32(tail, it) == 0x06054b50L && it + 22 + u16(tail, it + 20) == tailSize } ?: error("Invalid ZIP directory")
            val count = u16(tail, end + 10)
            require(u16(tail, end + 4) == 0 && u16(tail, end + 6) == 0 && u16(tail, end + 8) == count && count in 1..10000) { "Split/ZIP64 archives are unsupported" }
            val offset = u32(tail, end + 16)
            val size = u32(tail, end + 12)
            require(offset + size <= file.length() - tailSize + end) { "Invalid ZIP directory bounds" }
            input.seek(offset)
            repeat(count) {
                val header = ByteArray(46); input.readFully(header)
                require(u32(header, 0) == 0x02014b50L) { "Invalid ZIP entry" }
                val mode = ((u32(header, 38) shr 16).toInt() and 0xf000)
                require(mode in setOf(0, 0x8000, 0x4000)) { "ZIP symlinks and special files are unsupported" }
                require(u16(header, 28) in 1..1024) { "Invalid ZIP entry name" }
                input.seek(input.filePointer + u16(header, 28) + u16(header, 30) + u16(header, 32))
                require(input.filePointer <= offset + size) { "Invalid ZIP entry bounds" }
            }
        }
    }
    fun extractInstaller(zipFile: File, destination: File) {
        ZipFile(zipFile).use { zip ->
            zip.getInputStream(zip.getEntry(INSTALLER)).use { input ->
                destination.outputStream().use { copyBounded(input, it, MAX_INSTALLER_BYTES) }
            }
        }
    }
}

class AnyKernelInstaller(private val executor: CommandExecutor, private val busybox: String) {
    suspend fun install(zip: File, workspace: File, onOutput: (String) -> Unit): CommandResult {
        ArtifactFiles.validateAnyKernel(zip)
        val installer = File(workspace, "update-binary")
        ArtifactFiles.extractInstaller(zip, installer)
        val script = """
            set -e
            cd ${shellQuote(workspace.absolutePath)}
            ${shellQuote(busybox)} mkdir -p ${shellQuote(File(workspace, "tmp").absolutePath)}
            ASH_STANDALONE=1 AKHOME=${shellQuote(File(workspace, "tmp").absolutePath)} POSTINSTALL=${shellQuote(workspace.absolutePath)} ${shellQuote(busybox)} ash ${shellQuote(installer.absolutePath)} 3 1 ${shellQuote(zip.absolutePath)}
        """.trimIndent()
        return executor.execute(script, onOutput = onOutput)
    }
}
