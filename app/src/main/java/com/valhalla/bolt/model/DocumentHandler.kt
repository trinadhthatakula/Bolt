package com.valhalla.bolt.model

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

class DocumentHandler(val context: Context) {
    fun displayName(uri: Uri): String = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()?.take(160) ?: "Selected file"
    fun stage(uri: Uri, limit: Long = ArtifactFiles.MAX_ZIP_BYTES): File {
        require(uri.scheme in setOf("content", "file")) { "Only readable document URIs are supported" }
        val directory = ArtifactFiles.workspace(File(context.cacheDir, "imports"))
        val destination = File(directory, "artifact")
        try {
            val available = (directory.usableSpace - 64L * 1024 * 1024).coerceAtLeast(0)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Document provider returned no readable stream" }
                destination.outputStream().use { output ->
                    require(ArtifactFiles.copyBounded(input, output, minOf(limit, available)) > 0) { "Selected file is empty" }
                }
            }
            return destination
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }
    private fun readManifest(uri: Uri): RecoveryManifest = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Cannot read backup manifest" }
        val bytes = java.io.ByteArrayOutputStream()
        ArtifactFiles.copyBounded(input, bytes, 1024 * 1024)
        RecoveryManifest.decode(bytes.toString(Charsets.UTF_8.name()))
    }
    private fun session(name: String, manifestUri: Uri?, images: Map<String, Uri>, automatic: Boolean = false, fallbackDate: Long = 0): BackupSession {
        val manifest = runCatching { readManifest(requireNotNull(manifestUri)) }.getOrNull()
        val error = if (manifest == null) "Unverified legacy or incomplete backup. A complete Bolt manifest is required for restore." else null
        val files = images.map { (partition, uri) ->
            BackupFile(uri, "$partition.img", partition, manifest, images, error ?: if (manifest!!.images.none { it.partition == partition }) "Image is missing from the manifest" else null)
        }
        val before = if (automatic && manifest != null) runCatching {
            val history = org.json.JSONArray(File(context.filesDir, "flash-history.json").readText())
            (0 until history.length()).map { history.getJSONObject(it) }.firstOrNull { it.optString("id") == manifest.id }?.getString("file")
        }.getOrNull() else null
        return BackupSession(name, files.sortedBy { it.partitionName }, manifest?.id ?: manifestUri?.toString() ?: name, automatic,
            manifest?.createdAt ?: fallbackDate, if (automatic) "On this device" else "Selected backup directory", before)
    }
    fun localRecoverySessions(): List<BackupSession> = File(context.filesDir, "recovery").listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { directory ->
        val images = directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".img") }.associate { it.name.removeSuffix(".img") to Uri.fromFile(it) }
        if (images.isEmpty()) null else session("Automatic recovery ${directory.name.take(8)}", File(directory, "manifest.json").takeIf { it.exists() }?.let(Uri::fromFile), images, automatic = true, fallbackDate = directory.lastModified())
    }
    fun listBackupSessions(backupUri: Uri): List<BackupSession> {
        val parent = DocumentFile.fromTreeUri(context, backupUri) ?: throw IOException("Invalid backup directory")
        val root = parent.findFile(BACKUP_DIR_NAME) ?: return emptyList()
        return root.listFiles().filter { it.isDirectory }.mapNotNull { directory ->
            val images = directory.listFiles().filter { it.isFile && it.name?.endsWith(".img") == true }.associate { it.name!!.removeSuffix(".img") to it.uri }
            if (images.isEmpty()) null else session(directory.name ?: "Backup", directory.findFile("manifest.json")?.uri, images, fallbackDate = directory.lastModified())
        }.sortedByDescending { it.createdAt }
    }
    fun createBackupSessionDir(destinationUri: Uri, sessionName: String): Result<DocumentFile> = runCatching {
        val parent = DocumentFile.fromTreeUri(context, destinationUri) ?: error("Invalid backup directory")
        val root = parent.findFile(BACKUP_DIR_NAME) ?: parent.createDirectory(BACKUP_DIR_NAME) ?: error("Cannot create backup directory")
        require(root.findFile(sessionName) == null) { "Backup session already exists" }
        root.createDirectory(sessionName) ?: error("Cannot create backup session")
    }
    fun writeVerified(document: DocumentFile, source: File) {
        context.contentResolver.openOutputStream(document.uri, "wt").use { output ->
            requireNotNull(output) { "Cannot write backup document" }
            source.inputStream().use { ArtifactFiles.copyBounded(it, output, source.length()) }
        }
        val hash = context.contentResolver.openInputStream(document.uri).use { input ->
            requireNotNull(input) { "Cannot verify exported backup" }
            ArtifactFiles.sha256(input, source.length())
        }
        require(hash == ArtifactFiles.sha256(source)) { "Exported backup digest does not match" }
    }
    fun writeManifest(directory: DocumentFile, manifest: RecoveryManifest) {
        val document = directory.createFile("application/json", "manifest.json") ?: error("Cannot create backup manifest")
        context.contentResolver.openOutputStream(document.uri, "wt").use { stream ->
            requireNotNull(stream).write(manifest.encode().toByteArray())
        }
        require(readManifest(document.uri) == manifest) { "Exported manifest verification failed" }
    }
    fun exists(destinationUri: Uri, vararg pathSegments: String): Boolean = runCatching {
        var document = DocumentFile.fromTreeUri(context, destinationUri)
        for (segment in pathSegments) document = document?.findFile(segment)
        document?.exists() == true
    }.getOrDefault(false)
    fun canAccessUri(uri: Uri): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission } &&
            DocumentFile.fromTreeUri(context, uri)?.isDirectory == true
    }.getOrDefault(false)
}
