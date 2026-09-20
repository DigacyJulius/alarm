package com.shiftalarm.app.core

import com.shiftalarm.app.calendar.CalEvent
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.AppSettings
import com.shiftalarm.app.data.ShiftConfig
import com.shiftalarm.app.data.ShiftType
import com.shiftalarm.app.data.WorkProfile
import java.util.Calendar
import java.util.concurrent.TimeUnit

object RuleEngine {

    fun matchProfile(event: CalEvent, profiles: List<WorkProfile>): WorkProfile? {
        val hay = (event.location + " " + event.title).lowercase()
        return profiles.firstOrNull { p ->
            p.keywords.split(",", "\uff0c")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .any { hay.contains(it) }
        }
    }

    fun isOffDay(event: CalEvent, profile: WorkProfile): Boolean {
        val off = profile.offKeyword.trim().lowercase()
        return off.isNotEmpty() && event.title.lowercase().contains(off)
    }

    fun pickShift(event: CalEvent, profile: WorkProfile, settings: AppSettings): ShiftConfig? {
        val title = event.title.lowercase()
        val matches = profile.shifts.filter {
            it.keyword.isNotBlank() && title.contains(it.keyword.trim().lowercase())
        }
        if (matches.isNotEmpty()) {
            return matches.maxByOrNull { it.keyword.trim().length }
        }
        return when (shiftOf(hourOf(event.begin), settings)) {
            ShiftType.MORNING -> profile.shifts.getOrNull(0)
            ShiftType.AFTERNOON -> profile.shifts.getOrNull(1)
            ShiftType.NIGHT -> profile.shifts.getOrNull(2)
        }
    }

    fun shiftOf(startHour: Int, s: AppSettings): ShiftType {
        val h = if (startHour < 4) startHour + 24 else startHour
        return when {
            h >= s.morningStart && h < s.morningEnd -> ShiftType.MORNING
            h >= s.afternoonStart && h < s.afternoonEnd -> ShiftType.AFTERNOON
            h >= s.nightStart && h < s.nightEnd -> ShiftType.NIGHT
            else -> ShiftType.MORNING
        }
    }

    fun buildWorkAlarms(
        event: CalEvent,
        profile: WorkProfile,
        settings: AppSettings
    ): List<AlarmEntry> {
        val cfg = pickShift(event, profile, settings) ?: return emptyList()
        // Event-based work alarms occupy the 100M..150M id range.
        return buildAlarmsForShift(cfg, profile, event.begin, event.instanceId % 5_000_000L, 100_000_000L)
            .map { it.copy(groupId = event.instanceId) }
    }

    /** Build alarms for an explicitly chosen shift on a given day.
     *  Used by the in-app Calendar page where the user picks the profile
     *  and shift directly, so no keyword/hour matching is involved.
     *  [idSeed] keeps alarm ids stable across syncs (for delete/restore).
     *  [idBase] selects the id RANGE so different alarm sources never
     *  collide: events 100M..150M, manual shifts 150M..200M, normal alarms
     *  200M..250M, snooze 250M..260M, test 260M, timer 280M. */
    fun buildAlarmsForShift(
        cfg: ShiftConfig,
        profile: WorkProfile,
        dayMillis: Long,
        idSeed: Long,
        idBase: Long = 100_000_000L
    ): List<AlarmEntry> {
        val (h, m) = parseTime(cfg.wakeTime)
        val base = Calendar.getInstance().apply {
            timeInMillis = dayMillis
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseId = idBase + (idSeed % 5_000_000L) * 10L
        val result = mutableListOf<AlarmEntry>()
        for (i in 0..cfg.alarmCount.coerceIn(0, 9)) {
            val t = base.timeInMillis + TimeUnit.MINUTES.toMillis(cfg.intervalMin.toLong() * i)
            val suffix = if (i > 0) "\uff08\u5f8c\u5099 $i\uff09" else ""
            result += AlarmEntry(
                id = baseId + i,
                groupId = baseId,
                triggerAt = t,
                label = profile.name + " \u00b7 " + cfg.name + suffix,
                kind = "work"
            )
        }
        return result
    }

    fun parseTime(s: String): Pair<Int, Int> {
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: 5
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return h.coerceIn(0, 23) to m.coerceIn(0, 59)
    }

    fun hourOf(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY)
    }
}
