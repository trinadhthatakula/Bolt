package com.valhalla.bolt.model

import java.io.File

interface PartitionAccess {
    suspend fun inventory(): List<Partition>
    suspend fun getActiveSlotSuffix(): String?
    suspend fun backup(partition: Partition, destination: File, onOutput: (String) -> Unit = {})
    suspend fun restore(partition: Partition, source: File, expectedHash: String, onOutput: (String) -> Unit)
}

data class TargetOutcome(val partition: String, val state: String, val error: String? = null)
data class RestoreResult(val outcomes: List<TargetOutcome>) {
    val isSuccess: Boolean get() = outcomes.isNotEmpty() && outcomes.all { it.state == "Verified" }
}

class RecoveryWorkflow(private val partitions: PartitionAccess) {
    suspend fun snapshot(targets: List<Partition>, directory: File, id: String, identity: DeviceIdentity, onOutput: (String) -> Unit, onTarget: (TargetOutcome) -> Unit = {}): RecoveryManifest {
        require(targets.isNotEmpty() && targets.map { it.name }.distinct().size == targets.size) { "Invalid recovery inventory" }
        require(directory.usableSpace > targets.sumOf { it.sizeBytes } + 64L * 1024 * 1024) { "Insufficient recovery storage" }
        val images = targets.map { target ->
            require(PartitionPolicy.validName(target.name) && target.sizeBytes > 0)
            onTarget(TargetOutcome(target.name, "Backing up"))
            val file = File(directory, "${target.name}.img")
            require(file.createNewFile()) { "Recovery output already exists" }
            partitions.backup(target, file, onOutput)
            require(file.length() == target.sizeBytes) { "Recovery image is truncated" }
            val image = RecoveryImage(target.name, file.length(), ArtifactFiles.sha256(file), PartitionPolicy.suffix(target.name), target.path)
            onTarget(TargetOutcome(target.name, "Backed up"))
            image
        }
        return RecoveryManifest(id, identity, System.currentTimeMillis(), images)
    }
    suspend fun restore(manifest: RecoveryManifest, selected: String, both: Boolean, identity: DeviceIdentity, files: Map<String, File>, onOutput: (String) -> Unit, beforeWrite: () -> Unit, onTarget: (TargetOutcome) -> Unit = {}): RestoreResult {
        val inventory = partitions.inventory()
        val plan = PartitionPolicy.restorePlan(manifest, selected, both, identity, partitions.getActiveSlotSuffix(), inventory)
        plan.forEach { image ->
            val file = files[image.partition] ?: error("Backup image ${image.partition} is missing")
            require(file.length() == image.size && ArtifactFiles.sha256(file) == image.sha256) { "Backup ${image.partition} failed size/digest verification" }
        }
        val outcomes = mutableListOf<TargetOutcome>()
        for (image in plan) {
            beforeWrite()
            onTarget(TargetOutcome(image.partition, "Writing"))
            val outcome = try {
                partitions.restore(inventory.single { it.name == image.partition }, files.getValue(image.partition), image.sha256, onOutput)
                TargetOutcome(image.partition, "Verified")
            } catch (e: Exception) { TargetOutcome(image.partition, "Failed / possibly partial", e.message) }
            outcomes.add(outcome); onTarget(outcome)
            if (outcome.error != null) {
                plan.drop(outcomes.size).forEach { remaining ->
                    TargetOutcome(remaining.partition, "Not attempted").also { outcomes.add(it); onTarget(it) }
                }
                break
            }
        }
        return RestoreResult(outcomes)
    }
}

class KernelFlashWorkflow(private val partitions: PartitionAccess, private val installer: AnyKernelInstaller) {
    suspend fun flash(zip: File, expectedHash: String, workspace: File, recoveryDirectory: File, targets: List<Partition>, id: String, identity: DeviceIdentity, commit: (RecoveryManifest) -> Unit, onOutput: (String) -> Unit, beforeInstall: () -> Unit, onTarget: (TargetOutcome) -> Unit = {}): CommandResult {
        require(ArtifactFiles.sha256(zip) == expectedHash) { "Staged ZIP changed" }
        ArtifactFiles.validateAnyKernel(zip)
        val manifest = RecoveryWorkflow(partitions).snapshot(targets, recoveryDirectory, id, identity, onOutput, onTarget)
        commit(manifest) // A missing/uncommitted recovery manifest must stop installer execution.
        beforeInstall()
        return installer.install(zip, workspace, onOutput)
    }
}
