package com.valhalla.bolt.model

import java.io.File

class PartitionHelper(private val shell: CommandExecutor, private val runtime: RootRuntime) : PartitionAccess {
    private val recommended = setOf("boot", "init_boot", "vendor_boot", "dtbo", "vbmeta", "recovery")
    private val excluded = setOf("system", "system_ext", "vendor", "product", "userdata", "cache", "metadata", "super", "vda")
    private fun tool(name: String) = "${shellQuote(runtime.busybox)} $name"

    override suspend fun inventory(): List<Partition> {
        val script = """
            set -e
            for d in /dev/block/by-name /dev/block/bootdevice/by-name /dev/block/platform/*/by-name /dev/block/platform/*/*/by-name; do
                [ -d "${'$'}d" ] || continue
                for p in "${'$'}d"/*; do
                    [ -b "${'$'}p" ] || continue
                    n="${'$'}{p##*/}"
                    path=${'$'}(${tool("readlink")} -f "${'$'}p")
                    size=${'$'}(${tool("blockdev")} --getsize64 "${'$'}p")
                    printf '%s\t%s\t%s\n' "${'$'}n" "${'$'}path" "${'$'}size"
                done
            done
        """.trimIndent()
        val candidates = shell.execute(script, 30).requireSuccess("Partition discovery").output.lineSequence().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 3 || !PartitionPolicy.validName(fields[0])) return@mapNotNull null
            val size = fields[2].toLongOrNull() ?: return@mapNotNull null
            if (!fields[1].startsWith("/dev/block/") || size <= 0) return@mapNotNull null
            val base = fields[0].removeSuffix(PartitionPolicy.suffix(fields[0]) ?: "")
            if (base in excluded) return@mapNotNull null
            Partition(fields[0], fields[1], size)
        }.toList()
        require(candidates.groupBy { it.name }.values.all { group -> group.map { it.path to it.sizeBytes }.distinct().size == 1 }) { "Ambiguous partition names in by-name inventory" }
        return candidates.distinctBy { it.name }.groupBy { it.path }.values.flatMap { aliases ->
            val slotted = aliases.filter { PartitionPolicy.suffix(it.name) != null }
            require(slotted.mapNotNull { PartitionPolicy.suffix(it.name) }.distinct().size <= 1) { "Two slots resolve to the same block device" }
            if (slotted.isNotEmpty()) slotted else aliases.take(1)
        }
    }
    override suspend fun getActiveSlotSuffix(): String? {
        val value = shell.execute("getprop ro.boot.slot_suffix", 15).requireSuccess("Slot discovery").output.trim()
        return value.takeIf { it == "_a" || it == "_b" }
    }
    suspend fun getCategorizedPartitions(): CategorizedPartitions {
        val active = getActiveSlotSuffix()
        val (first, second) = inventory().partition {
            val suffix = PartitionPolicy.suffix(it.name)
            val base = it.name.removeSuffix(suffix ?: "")
            base in recommended && (suffix == null || suffix == active)
        }
        return CategorizedPartitions(first, second)
    }
    suspend fun recoveryTargets(): List<Partition> {
        val active = getActiveSlotSuffix()
        val candidates = inventory().filter { it.name.removeSuffix(PartitionPolicy.suffix(it.name) ?: "") in recommended }
        require(candidates.any { it.name.removeSuffix(PartitionPolicy.suffix(it.name) ?: "") in setOf("boot", "init_boot", "recovery") }) { "No boot, init_boot or recovery target was discovered; flashing is blocked without kernel recovery" }
        require(candidates.none { PartitionPolicy.suffix(it.name) != null } || active != null) { "Active slot is unknown; no kernel operation is allowed" }
        return candidates.filter { PartitionPolicy.suffix(it.name) == null || PartitionPolicy.suffix(it.name) == active }
    }
    private fun targetCheck(partition: Partition): String {
        require(PartitionPolicy.validName(partition.name) && partition.path.startsWith("/dev/block/") && partition.sizeBytes > 0)
        val path = shellQuote(partition.path)
        return "[ -b $path ] && [ \"${'$'}(${tool("blockdev")} --getsize64 $path)\" = '${partition.sizeBytes}' ]"
    }
    override suspend fun backup(partition: Partition, destination: File, onOutput: (String) -> Unit) {
        shell.execute("set -e\n${targetCheck(partition)}\n${tool("dd")} if=${shellQuote(partition.path)} of=${shellQuote(destination.absolutePath)} bs=1048576 conv=fsync\nchmod 600 ${shellQuote(destination.absolutePath)}", onOutput = onOutput).requireSuccess("Backup ${partition.name}")
        require(destination.length() == partition.sizeBytes) { "Backup size mismatch for ${partition.name}" }
    }
    override suspend fun restore(partition: Partition, source: File, expectedHash: String, onOutput: (String) -> Unit) {
        require(source.length() == partition.sizeBytes && ArtifactFiles.sha256(source) == expectedHash) { "Restore image failed integrity validation" }
        runtime.locked(shell).execute("set -e\n${targetCheck(partition)}\n${tool("dd")} if=${shellQuote(source.absolutePath)} of=${shellQuote(partition.path)} bs=1048576 conv=fsync", onOutput = onOutput).requireSuccess("Write ${partition.name}")
        val hash = shell.execute("${tool("sha256sum")} ${shellQuote(partition.path)}", onOutput = onOutput).requireSuccess("Readback ${partition.name}").output.trim().substringBefore(' ')
        require(hash == expectedHash) { "Readback verification failed for ${partition.name}" }
    }
}
