package com.valhalla.bolt.model

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

data class DeviceIdentity(val id: String, val device: String, val fingerprint: String) {
    companion object {
        fun current(context: Context) = DeviceIdentity(
            requireNotNull(Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)),
            Build.DEVICE, Build.FINGERPRINT
        )
    }
}

data class RecoveryImage(val partition: String, val size: Long, val sha256: String, val slot: String?, val targetPath: String)
data class RecoveryManifest(val id: String, val device: DeviceIdentity, val createdAt: Long, val images: List<RecoveryImage>) {
    fun encode(): String = JSONObject().apply {
        put("version", 1); put("complete", true); put("id", id); put("createdAt", createdAt)
        put("device", JSONObject().put("id", device.id).put("name", device.device).put("fingerprint", device.fingerprint))
        put("images", JSONArray().apply { images.forEach { image ->
            put(JSONObject().put("partition", image.partition).put("size", image.size).put("sha256", image.sha256).put("slot", image.slot ?: JSONObject.NULL).put("targetPath", image.targetPath))
        } })
    }.toString(2)
    companion object {
        fun decode(text: String): RecoveryManifest {
            require(text.length <= 1024 * 1024) { "Backup manifest is too large" }
            val json = JSONObject(text)
            require(json.getInt("version") == 1 && json.getBoolean("complete")) { "Unsupported or incomplete backup manifest" }
            val device = json.getJSONObject("device")
            val array = json.getJSONArray("images")
            require(array.length() in 1..128) { "Invalid backup inventory" }
            val images = (0 until array.length()).map { index ->
                val image = array.getJSONObject(index)
                val name = image.getString("partition")
                require(PartitionPolicy.validName(name)) { "Invalid partition name in manifest" }
                val size = image.getLong("size")
                val hash = image.getString("sha256")
                val slot = if (image.isNull("slot")) null else image.getString("slot")
                require(size > 0 && hash.matches(Regex("[a-f0-9]{64}")) && slot == PartitionPolicy.suffix(name)) { "Invalid image metadata" }
                val path = image.getString("targetPath")
                require(path.startsWith("/dev/block/") && '\u0000' !in path) { "Invalid target identity" }
                RecoveryImage(name, size, hash, slot, path)
            }
            require(images.map { it.partition }.distinct().size == images.size) { "Duplicate backup partitions" }
            return RecoveryManifest(json.getString("id"), DeviceIdentity(device.getString("id"), device.getString("name"), device.getString("fingerprint")), json.getLong("createdAt"), images)
        }
    }
}

object PartitionPolicy {
    fun validName(name: String): Boolean = name.matches(Regex("[A-Za-z0-9_-]{1,64}"))
    fun suffix(name: String): String? = when {
        name.endsWith("_a") -> "_a"
        name.endsWith("_b") -> "_b"
        else -> null
    }
    fun restorePlan(manifest: RecoveryManifest, selected: String, both: Boolean, identity: DeviceIdentity, activeSlot: String?, inventory: List<Partition>): List<RecoveryImage> {
        require(manifest.device == identity) { "Backup belongs to a different device, installation or build" }
        val image = manifest.images.singleOrNull { it.partition == selected } ?: error("Image is not in the verified backup manifest")
        val targets = if (both) {
            require(image.slot != null && activeSlot in setOf("_a", "_b")) { "Both Slots requires a known A/B slot" }
            val base = image.partition.removeSuffix(image.slot)
            listOf("${base}_a", "${base}_b").map { name ->
                manifest.images.singleOrNull { it.partition == name } ?: error("Both Slots requires the original verified backup for $name; an active image is never cloned")
            }
        } else {
            require(image.slot == null || image.slot == activeSlot) { "Backup does not match the known active slot" }
            listOf(image)
        }
        targets.forEach { target ->
            val partition = inventory.singleOrNull { it.name == target.partition } ?: error("Backup target is not in the live partition inventory")
            require(partition.sizeBytes == target.size && target.size > 0) { "Backup size does not match target capacity" }
            require(partition.path == target.targetPath) { "Backup target resolves to a different block device" }
        }
        return targets
    }
}
