package com.valhalla.bolt.model

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DocumentHandler(private val context: Context) {

    fun listBackupSessions(backupUri: Uri): List<BackupSession> {
        Log.d("DocumentHandler", "listBackupSessions: backUpUri =  $backupUri")
        val parentDoc = DocumentFile.fromTreeUri(context, backupUri)
            ?: throw IOException("Invalid backup directory")
        val backupDir = parentDoc.findFile(BACKUP_DIR_NAME)
            ?: throw IOException(
                "'$BACKUP_DIR_NAME' folder not found in backup directory ${parentDoc.uri} of list ${
                parentDoc.listFiles().joinToString("\n") { file -> file.name.toString() }
            }")
        Log.d("DocumentHandler", "backup dir found ${backupDir.uri}")

        return backupDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith("Backup_") == true }
            .map { sessionDir ->
                val backupFiles = sessionDir.listFiles()
                    .filter { it.name?.endsWith(".img") == true }
                    .mapNotNull { docFile ->
                        val partitionName = docFile.name?.removeSuffix(".img") ?: return@mapNotNull null

                        // --- THE KEY CHANGE ---
                        // We no longer create a temp file here.
                        // We just create our data class with the URI.
                        BackupFile(
                            uri = docFile.uri,
                            fileName = docFile.name!!,
                            partitionName = partitionName
                        )
                    }
                BackupSession(sessionName = sessionDir.name!!, files = backupFiles)
            }
            .filter { it.files.isNotEmpty() }
            .sortedByDescending { it.sessionName }
    }

    fun copyToCache(uri: Uri): Result<File> {
        return try {
            val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "temp_${System.currentTimeMillis()}"
            val tempFile = File(context.cacheDir, fileName)

            context.contentResolver.openInputStream(uri).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input!!.copyTo(output)
                }
            }
            Result.success(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    fun createBackupSessionDir(destinationUri: Uri, sessionName: String): Result<DocumentFile> {
        return try {
            val parentDoc = DocumentFile.fromTreeUri(context, destinationUri)
                ?: return Result.failure(Exception("Invalid backup directory"))
            val backupDir =
                parentDoc.findFile(BACKUP_DIR_NAME) ?: parentDoc.createDirectory(BACKUP_DIR_NAME)
                ?: return Result.failure(Exception("Could not create backup directory"))

            val sessionDir = backupDir.createDirectory(sessionName)
            if (sessionDir != null) {
                Result.success(sessionDir)
            } else {
                Result.failure(Exception("Could not create session directory"))
            }
        } catch (e: IOException) {
            Result.failure(e)

        }
    }

    fun exists(destinationUri: Uri, vararg pathSegments: String): Boolean {
        // Start with the base directory
        var currentDoc = DocumentFile.fromTreeUri(context, destinationUri)

        // If the base URI itself isn't valid, we can't proceed.
        if (currentDoc?.exists() != true) {
            return false
        }

        // Walk through each segment of the path
        for (segment in pathSegments) {
            val nextDoc = currentDoc?.findFile(segment)
            if (nextDoc?.exists() == true) {
                // Move to the next level down the tree
                currentDoc = nextDoc
            } else {
                // If any segment is not found, the path doesn't exist.
                Log.e("DocumentHandler", "Path segment not found: $segment")
                return false
            }
        }

        // If we successfully walked through all segments, the path exists.
        return true
    }

    fun canAccessUri(uri: Uri): Boolean {
        // Check if we have a persisted permission grant for this URI
        val hasPersistedPermission = context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission && it.isWritePermission }

        if (!hasPersistedPermission) {
            return false
        }

        // Check if we can actually resolve it to a document tree
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        return documentFile?.exists() == true && documentFile.isDirectory
    }

    fun cacheFile(fileName: String) = File(context.cacheDir, fileName)

    fun writeToFile(finalFile: DocumentFile, tempFile: File) {
        context.contentResolver.openOutputStream(finalFile.uri).use { out ->
            tempFile.inputStream().use { it.copyTo(out!!) }
        }
    }

}