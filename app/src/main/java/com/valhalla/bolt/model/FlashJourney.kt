package com.valhalla.bolt.model

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class BootStatus { REBOOT_PENDING, REBOOT_OBSERVED, USER_CONFIRMED, ISSUE_REPORTED }

data class FlashJourney(
    val id: String,
    val fileName: String,
    val deviceId: String,
    val bootIdBefore: String?,
    val startedAt: Long,
    val completedAt: Long? = null,
    val bootObservedAt: Long? = null,
    val feedback: String? = null,
) {
    val bootStatus: BootStatus get() = when {
        feedback == "working" -> BootStatus.USER_CONFIRMED
        feedback == "issue" -> BootStatus.ISSUE_REPORTED
        bootObservedAt != null -> BootStatus.REBOOT_OBSERVED
        else -> BootStatus.REBOOT_PENDING
    }
    val statusLabel: String get() = when (bootStatus) {
        BootStatus.REBOOT_PENDING -> "Reboot to apply"
        BootStatus.REBOOT_OBSERVED -> "Device restarted"
        BootStatus.USER_CONFIRMED -> "You confirmed it’s working"
        BootStatus.ISSUE_REPORTED -> "Issue reported after install"
    }
}

/** A new boot proves a restart, independently of installer success or kernel feature health. */
fun FlashJourney.observeBoot(deviceIdNow: String, bootIdNow: String?, now: Long): FlashJourney =
    if (completedAt != null && bootObservedAt == null && this.deviceId == deviceIdNow &&
        bootIdBefore != null && bootIdNow != null && bootIdBefore != bootIdNow && now >= completedAt
    ) copy(bootObservedAt = now) else this

class FlashJourneyStore(context: Context) {
    private val journal = AtomicFile(File(context.filesDir, "flash-journeys.json"))
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()

    private fun load(): List<FlashJourney> = runCatching {
        val array = JSONArray(journal.openRead().bufferedReader().use { it.readText() })
        (0 until array.length()).map { index -> array.getJSONObject(index).let { item ->
            FlashJourney(item.getString("id"), item.getString("file"), item.getString("device"),
                item.optString("bootBefore").takeIf { it.isNotEmpty() }, item.getLong("started"),
                item.optLong("completed").takeIf { it > 0 }, item.optLong("observed").takeIf { it > 0 },
                item.optString("feedback").takeIf { it.isNotEmpty() })
        } }
    }.getOrDefault(emptyList())

    @Synchronized private fun save(records: List<FlashJourney>) {
        val array = JSONArray().apply { records.take(50).forEach { record -> put(JSONObject()
            .put("id", record.id).put("file", record.fileName).put("device", record.deviceId)
            .put("bootBefore", record.bootIdBefore ?: "").put("started", record.startedAt)
            .put("completed", record.completedAt ?: 0).put("observed", record.bootObservedAt ?: 0)
            .put("feedback", record.feedback ?: "")) } }
        val stream = journal.startWrite()
        try { stream.write(array.toString().toByteArray()); journal.finishWrite(stream) }
        catch (e: Exception) { journal.failWrite(stream); throw e }
        mutable.value = records.take(50)
    }

    @Synchronized fun begin(id: String, file: String, deviceId: String, bootId: String?) {
        save(listOf(FlashJourney(id, file, deviceId, bootId, System.currentTimeMillis())) + mutable.value.filterNot { it.id == id })
    }
    @Synchronized fun complete(id: String) { save(mutable.value.map { if (it.id == id) it.copy(completedAt = System.currentTimeMillis()) else it }) }
    @Synchronized fun addLegacy(id: String, file: String, deviceId: String) {
        if (mutable.value.any { it.id == id }) return
        val now = System.currentTimeMillis()
        save(listOf(FlashJourney(id, file, deviceId, null, now, completedAt = now)) + mutable.value)
    }
    @Synchronized fun observeLatest(deviceId: String, bootId: String?) {
        // One reboot reconciles the latest completed job, not every historic install.
        val latest = mutable.value.firstOrNull()?.takeIf { it.completedAt != null } ?: return
        val observed = latest.observeBoot(deviceId, bootId, System.currentTimeMillis())
        if (observed != latest) save(mutable.value.map { if (it.id == latest.id) observed else it })
    }
    @Synchronized fun feedback(id: String, working: Boolean) {
        save(mutable.value.map { if (it.id == id && it.completedAt != null) it.copy(feedback = if (working) "working" else "issue") else it })
    }
}
