package com.shiftalarm.app.core

import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.AppSettings
import com.shiftalarm.app.data.NormalAlarm
import com.shiftalarm.app.data.ShiftEntry
import com.shiftalarm.app.data.WorkProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Backup payload: everything the user created or configured, minus volatile
 * state (scheduled alarms are rebuilt by the next sync, cached .ics events
 * are re-importable). Small enough to keep in cloud storage forever.
 */
@Serializable
data class BackupPayload(
    val version: Int = 1,
    val savedAt: Long,
    val settings: AppSettings,
    val profiles: List<WorkProfile>,
    val normalAlarms: List<NormalAlarm>,
    val manualShifts: List<ShiftEntry>,
    val worldClocks: List<String>,
    val dismissedAlarmIds: Set<Long>,
    val dismissedAlarmMeta: Map<Long, String>,
    val dismissedAlarmTimes: Map<Long, Long>,
    val dismissedGroups: Map<Long, Long>
)

object Backup {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun fileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "shiftlarm-backup-$stamp.json"
    }

    fun serialize(data: AppData): String = json.encodeToString(
        BackupPayload.serializer(),
        BackupPayload(
            savedAt = System.currentTimeMillis(),
            settings = data.settings,
            profiles = data.profiles,
            normalAlarms = data.normalAlarms,
            manualShifts = data.manualShifts,
            worldClocks = data.worldClocks,
            dismissedAlarmIds = data.dismissedAlarmIds,
            dismissedAlarmMeta = data.dismissedAlarmMeta,
            dismissedAlarmTimes = data.dismissedAlarmTimes,
            dismissedGroups = data.dismissedGroups
        )
    )

    /**
     * Merge a backup into the current data. Lists merge by id/date (backup
     * entries win on conflict) so nothing created on this phone is lost;
     * settings are replaced wholesale — a backup means "make it like that".
     * Returns null when the file is not a readable backup.
     */
    fun restoreInto(current: AppData, text: String): AppData? {
        val p = runCatching {
            json.decodeFromString(BackupPayload.serializer(), text)
        }.getOrNull() ?: return null

        val profiles = (current.profiles.filterNot { c -> p.profiles.any { it.id == c.id } } + p.profiles)
            .sortedBy { it.name }
        val normalAlarms = (current.normalAlarms.filterNot { c -> p.normalAlarms.any { it.id == c.id } } + p.normalAlarms)
            .sortedBy { it.hour * 60 + it.minute }
        val manualShifts = (current.manualShifts.filterNot { c ->
            p.manualShifts.any { it.date == c.date && it.profileId == c.profileId }
        } + p.manualShifts).sortedBy { it.date + "#" + it.profileId }

        return current.copy(
            settings = p.settings,
            profiles = profiles,
            normalAlarms = normalAlarms,
            manualShifts = manualShifts,
            worldClocks = (current.worldClocks + p.worldClocks).distinct(),
            dismissedAlarmIds = current.dismissedAlarmIds + p.dismissedAlarmIds,
            dismissedAlarmMeta = current.dismissedAlarmMeta + p.dismissedAlarmMeta,
            dismissedAlarmTimes = current.dismissedAlarmTimes + p.dismissedAlarmTimes,
            dismissedGroups = current.dismissedGroups + p.dismissedGroups
        )
    }
}
