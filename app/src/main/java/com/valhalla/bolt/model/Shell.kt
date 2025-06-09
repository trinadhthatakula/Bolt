package com.valhalla.bolt.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class Shell(private val useSu: Boolean) {

    sealed interface Result {
        data class Success(
            val exitCode: Int,
            val stdout: String,
            val stderr: String
        ) : Result

        data class Error(
            val exception: Throwable,
            val command: String
        ) : Result
    }

    suspend fun runCommand(vararg commands: String): List<Result> = withContext(Dispatchers.IO) {
        commands.map { command ->
            try {
                executeSingleCommand(command)
            } catch (e: Exception) {
                Result.Error(e, command)
            }
        }
    }

    private suspend fun executeSingleCommand(command: String): Result = withContext(Dispatchers.IO) {
        val fullCommand = if (useSu) {
            if (!isRootAvailable()) {
                throw SecurityException("Root access requested but not available")
            }
            arrayOf("su", "-c", command)
        } else {
            arrayOf("sh", "-c", command)
        }

        try {
            val process = ProcessBuilder(*fullCommand)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()

            Result.Success(exitCode, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            throw CommandExecutionException("Failed to execute command: $command", e)
        }
    }

    companion object {
        suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                process.waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }
    }

    private class CommandExecutionException(message: String, cause: Throwable) :
        Exception(message, cause)
}