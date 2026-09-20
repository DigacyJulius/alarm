package com.shiftalarm.app.data

import com.shiftalarm.app.calendar.CalEvent
import kotlinx.serialization.Serializable

enum class ShiftType(val label: String) {
    MORNING("早更"),
    AFTERNOON("午更"),
    NIGHT("晚更")
}

@Serializable
data class ShiftConfig(
    val name: String = "早更",
    val keyword: String = "",
    val wakeTime: String = "05:30",
    val alarmCount: Int = 2,
    val intervalMin: Int = 5
)

@Serializable
data class WorkProfile(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val keywords: String = "",
    val offKeyword: String = "off",
    val shifts: List<ShiftConfig> = listOf(
        ShiftConfig(name = "早更", keyword = "a", wakeTime = "05:30"),
        ShiftConfig(name = "午更", keyword = "p", wakeTime = "10:30"),
        ShiftConfig(name = "晚更", keyword = "n", wakeTime = "15:30"),
        ShiftConfig(name = "特別更", keyword = "a-night", wakeTime = "05:30")
    )
)

@Serializable
data class NormalAlarm(
    val id: Long = System.currentTimeMillis(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true
)

@Serializable
data class AppSettings(
    val icalUrl: String = "",
    val darkMode: String = "system",
    val morningStart: Int = 4,
    val morningEnd: Int = 12,
    val afternoonStart: Int = 12,
    val afternoonEnd: Int = 18,
    val nightStart: Int = 18,
    val nightEnd: Int = 28,
    val snoozeMinutes: Int = 5,
    val lookaheadDays: Int = 7,
    // Empty = follow the device timezone. When set (e.g. "Asia/Hong_Kong"),
    // normal alarms fire at that region's local time — useful when travelling.
    val alarmTimezone: String = "",
    // "en" (default) or "zh"
    val language: String = "en",
    val tutorialDone: Boolean = false,
    val adsRemoved: Boolean = false,
    val adUnitId: String = "",
    // Device calendars (CalendarProvider ids) selected as the roster source.
    // Empty = not used. Only takes effect when no .ics file is imported.
    val deviceCalendarIds: Set<Long> = emptySet(),
    // Stable server-side keys of the selected calendars (survive renames
    // and provider id changes). Preferred over [deviceCalendarIds].
    val deviceCalendarNames: Set<String> = emptySet()
)

/** One manually-entered shift on the in-app Calendar page.
 *  [date] is a local date ("2026-09-14"); [shiftName] must match a
 *  ShiftConfig.name in the profile, or empty when [off] is true. */
@Serializable
data class ShiftEntry(
    val date: String,
    val profileId: Long,
    val shiftName: String,
    val off: Boolean = false
)

@Serializable
data class AlarmEntry(
    val id: Long,
    val groupId: Long,
    val triggerAt: Long,
    val label: String,
    val kind: String = "work",
    val isSnooze: Boolean = false
)

@Serializable
data class SyncLog(
    val time: Long,
    val text: String
)

@Serializable
data class AppData(
    val profiles: List<WorkProfile> = emptyList(),
    val normalAlarms: List<NormalAlarm> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val scheduled: List<AlarmEntry> = emptyList(),
    val dismissedGroups: Map<Long, Long> = emptyMap(),
    val dismissedAlarmIds: Set<Long> = emptySet(),
    val dismissedAlarmMeta: Map<Long, String> = emptyMap(),
    val dismissedAlarmTimes: Map<Long, Long> = emptyMap(),
    val syncLogs: List<SyncLog> = emptyList(),
    val icalEvents: List<CalEvent> = emptyList(),
    // World clock: user-selected timezone ids (IANA, e.g. "Asia/Tokyo").
    val worldClocks: List<String> = emptyList(),
    // Active timer: absolute end time in millis (0 = no timer running).
    val timerEndAt: Long = 0,
    // Manually-entered shifts from the in-app Calendar page.
    val manualShifts: List<ShiftEntry> = emptyList()
)
