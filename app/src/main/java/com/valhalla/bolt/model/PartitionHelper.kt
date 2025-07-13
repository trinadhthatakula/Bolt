package com.valhalla.bolt.model

import java.io.File

/**
 * Manages all partition-related operations like listing, backing up, and restoring.
 *
 * @param shell An instance of the Shell class with root access (useSu = true).
 */
class PartitionHelper(private val shell: Shell) {

    private val relevantPartitionNames = setOf("boot", "dtbo", "vbmeta", "vendor_boot", "recovery")
    // Partitions to explicitly exclude from both lists
    private val excludedPartitionNames = setOf("system", "userdata", "cache", "metadata")

    /**
     * Gets categorized lists of partitions available for backup.
     * It separates common, recommended partitions from other less common ones.
     */
    suspend fun getCategorizedPartitions(): CategorizedPartitions {
        val lsResult = shell.runCommand("ls /dev/block/by-name/").firstOrNull()
        val allPartitions = (lsResult as? Shell.Result.Success)?.stdout?.lines() ?: emptyList()
        val activeSuffix = getActiveSlotSuffix()

        val recommended = mutableListOf<Partition>()
        val other = mutableListOf<Partition>()

        val partitionsToScan = allPartitions.filter { partitionName ->
            // Filter out partitions we want to exclude entirely
            !excludedPartitionNames.any { excluded -> partitionName.contains(excluded) }
        }

        for (partitionName in partitionsToScan) {
            val baseName = if (activeSuffix != null) partitionName.removeSuffix(activeSuffix) else partitionName

            val partition = Partition(partitionName)
            if (relevantPartitionNames.contains(baseName)) {
                recommended.add(partition)
            } else {
                other.add(partition)
            }
        }

        // On A/B devices, ensure we only return partitions from the active slot
        return if (activeSuffix != null) {
            CategorizedPartitions(
                recommended = recommended.filter { it.name.endsWith(activeSuffix) },
                other = other.filter { it.name.endsWith(activeSuffix) }
            )
        } else {
            CategorizedPartitions(recommended, other)
        }
    }

    private suspend fun isAbDevice(): Boolean {
        val result = shell.runCommand("getprop ro.build.ab_update").firstOrNull()
        return result is Shell.Result.Success && result.stdout == "true"
    }

    suspend fun getActiveSlotSuffix(): String? {
        if (!isAbDevice()) return null
        val result = shell.runCommand("getprop ro.boot.slot_suffix").firstOrNull()
        return (result as? Shell.Result.Success)?.stdout?.trim()
    }

    suspend fun getAvailablePartitionsForBackup(): List<Partition> {
        val lsResult = shell.runCommand("ls /dev/block/by-name/").firstOrNull()
        val allPartitions = (lsResult as? Shell.Result.Success)?.stdout?.lines() ?: emptyList()
        val activeSuffix = getActiveSlotSuffix()

        return if (activeSuffix != null) {
            allPartitions.filter { partitionName ->
                val baseName = partitionName.removeSuffix(activeSuffix)
                relevantPartitionNames.contains(baseName) && partitionName.endsWith(activeSuffix)
            }.map { Partition(it) }
        } else {
            allPartitions.filter { relevantPartitionNames.contains(it) }.map { Partition(it) }
        }
    }

    suspend fun backupPartition(partitionName: String, destinationPath: String): Shell.Result {
        val source = "/dev/block/by-name/$partitionName"
        val command = "dd if=$source of=$destinationPath bs=1m"
        return shell.runCommand(command).first()
    }

    suspend fun restorePartition(sourcePath: String, partitionName: String): Shell.Result {
        val destination = "/dev/block/by-name/$partitionName"
        val command = "dd if=$sourcePath of=$destination bs=1m"
        return shell.runCommand(command).first()
    }
}