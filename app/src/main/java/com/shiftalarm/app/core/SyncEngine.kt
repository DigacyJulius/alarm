package com.shiftalarm.app.core

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shiftalarm.app.calendar.CalEvent
import com.shiftalarm.app.calendar.CalendarReader
import com.shiftalarm.app.calendar.IcalSource
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.Store
import com.shiftalarm.app.data.SyncLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class SyncResult(
    val total: Int,
    val matchedEvents: Int,
    val offDays: Int,
    val eventsRead: Int,
    val errors: List<String>,
    val next: AlarmEntry?
)

object SyncEngine {

    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val store = Store(context)
        val data = store.data.first()
        val now = System.currentTimeMillis()
        val dismissed = data.dismissedGroups
            .filterValues { now - it < TimeUnit.HOURS.toMillis(48) }
        val dismissedIds = data.dismissedAlarmIds
        // Deleted-alarm records whose original fire time has passed are no
        // longer useful: prune them so the "deleted alarms" list and the
        // restore buttons only ever show still-relevant entries. Entries
        // without a recorded time (legacy data) are kept.
        val expiredDeletes = data.dismissedAlarmTimes.filterValues { it <= now }.keys
        val activeDeletedIds = dismissedIds - expiredDeletes
        val activeDeletedMeta = data.dismissedAlarmMeta - expiredDeletes
        val activeDeletedTimes = data.dismissedAlarmTimes - expiredDeletes
        // Always look ahead at least 7 days so a full week of alarms is
        // fetched and shown, even if the stored setting is lower.
        val lookaheadDays = maxOf(data.settings.lookaheadDays, 7)
        val errors = mutableListOf<String>()
        var matchedEvents = 0
        var offDays = 0
        var eventsRead = 0

        val workEntries = mutableListOf<AlarmEntry>()
        if (data.profiles.isNotEmpty()) {
            // Work alarms scheduled by a previous sync that are still in the
            // future. A failed or suspicious re-sync must NEVER cancel them —
            // the user is relying on them to wake up.
            val retainedWork = data.scheduled.filter {
                it.kind == "work" && it.triggerAt > now &&
                    it.id !in activeDeletedIds && !dismissed.containsKey(it.groupId)
            }

            val from = now - TimeUnit.HOURS.toMillis(12)
            val to = now + TimeUnit.DAYS.toMillis(lookaheadDays.toLong())
            var readOk = true
            val events: List<CalEvent> = when {
                data.icalEvents.isNotEmpty() ->
                    data.icalEvents.filter { it.begin >= from && it.begin <= to }
                data.settings.deviceCalendarIds.isNotEmpty() || data.settings.deviceCalendarNames.isNotEmpty() -> {
                    // Device calendars (CalendarProvider). Needs READ_CALENDAR.
                    // Match by stable keys (survives renames) plus legacy ids.
                    try {
                        CalendarReader.queryEvents(
                            context, from, to,
                            data.settings.deviceCalendarIds,
                            data.settings.deviceCalendarNames
                        )
                    } catch (e: SecurityException) {
                        errors += t("Device calendar permission missing — kept existing alarms", "缺少日曆讀取權限——保留原有鬧鐘")
                        readOk = false
                        emptyList()
                    } catch (e: Exception) {
                        errors += t("Device calendar read failed: ", "讀取裝置日曆失敗：") + (e.message ?: e.toString())
                        readOk = false
                        emptyList()
                    }
                }
                data.settings.icalUrl.isNotBlank() -> {
                    try {
                        IcalSource.fetchEventsFromUrl(data.settings.icalUrl, from, to)
                    } catch (e: Exception) {
                        errors += t("iCal fetch failed: ", "iCal 抓取失敗：") + (e.message ?: e.toString())
                        readOk = false
                        emptyList()
                    }
                }
                else -> emptyList()
            }
            eventsRead = events.size
            for (ev in events) {
                val profile = RuleEngine.matchProfile(ev, data.profiles) ?: continue
                if (dismissed.containsKey(ev.instanceId)) continue
                // A manual entry on the in-app Calendar page for the same day
                // + profile OVERRIDES the synced event — once the user edits
                // that day, the calendar page is the source of truth.
                val evDate = java.time.Instant.ofEpochMilli(ev.begin)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                if (data.manualShifts.any { it.date == evDate && it.profileId == profile.id }) {
                    continue
                }
                if (RuleEngine.isOffDay(ev, profile)) {
                    offDays++
                    continue
                }
                val alarms = RuleEngine.buildWorkAlarms(ev, profile, data.settings)
                    .filter { it.triggerAt > now && it.id !in activeDeletedIds }
                if (alarms.isNotEmpty()) matchedEvents++
                workEntries += alarms
            }

            // Manual shifts entered on the in-app Calendar page. These are
            // local data, so they are always processed regardless of which
            // automatic source (or none) is configured. Alarm ids derive
            // from (date + profile) so they stay stable across syncs, letting
            // delete/restore and group dismissal work as usual.
            for (me in data.manualShifts) {
                val profile = data.profiles.firstOrNull { it.id == me.profileId } ?: continue
                val day = runCatching { java.time.LocalDate.parse(me.date) }.getOrNull() ?: continue
                val begin = day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                if (begin < from || begin > to) continue
                val idSeed = (me.date + "#" + me.profileId).hashCode().toLong().let {
                    if (it < 0) -it else it
                } % 5_000_000L
                // Manual-shift alarms occupy the 150M..200M id range (events
                // use 100M..150M) so the two sources can never collide.
                if (dismissed.containsKey(150_000_000L + idSeed * 10L)) continue
                if (me.off) {
                    offDays++
                    continue
                }
                val shift = profile.shifts.firstOrNull { it.name == me.shiftName } ?: continue
                val alarms = RuleEngine.buildAlarmsForShift(shift, profile, begin, idSeed, 150_000_000L)
                    .filter { it.triggerAt > now && it.id !in activeDeletedIds }
                if (alarms.isNotEmpty()) matchedEvents++
                workEntries += alarms
            }

            // If the roster could not be read, or a URL fetch came back
            // suspiciously empty (e.g. a server glitch returning an empty
            // calendar), keep the previously scheduled work alarms. Only a
            // healthy read may replace or remove them.
            val fromUrl = data.icalEvents.isEmpty() && data.settings.icalUrl.isNotBlank()
            val suspiciousEmpty = fromUrl && readOk && eventsRead == 0
            if ((!readOk || suspiciousEmpty) && retainedWork.isNotEmpty()) {
                val keep = retainedWork.filter { w -> workEntries.none { it.id == w.id } }
                workEntries += keep
                errors += if (readOk)
                    t("iCal read 0 events — kept ", "iCal 讀到 0 個事件，保留原有 ") + keep.size + t(" work alarms", " 粒工作鬧鐘")
                else
                    t("Network read failed — kept ", "網絡讀取失敗，保留原有 ") + keep.size + t(" work alarms", " 粒工作鬧鐘")
            }
        }

        val normalEntries = mutableListOf<AlarmEntry>()
        // Region alarm support: when the user picks a timezone, normal alarms
        // fire at that region's local time (e.g. 07:00 Hong Kong time even
        // while the device is in Tokyo). Work alarms follow the roster and
        // always use the device timezone.
        val alarmTz = data.settings.alarmTimezone
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { TimeZone.getTimeZone(it) }.getOrNull() }
            ?: TimeZone.getDefault()
        for (na in data.normalAlarms.filter { it.enabled }) {
            // ID range 200M..250M — must NOT overlap the snooze range
            // (250M..260M) or the test alarm id (260M), otherwise a
            // PendingIntent collision silently replaces an alarm.
            val baseId = 200_000_000L + (na.id % 5_000_000L) * 10L
            for (d in 0..lookaheadDays) {
                val cal = Calendar.getInstance(alarmTz)
                cal.set(Calendar.HOUR_OF_DAY, na.hour)
                cal.set(Calendar.MINUTE, na.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (d > 0) cal.add(Calendar.DAY_OF_YEAR, d)
                val t = cal.timeInMillis
                if (t <= now) continue
                if (na.days.isEmpty()) {
                    normalEntries += AlarmEntry(
                        baseId + d.coerceAtMost(9), na.id, t,
                        na.label.ifEmpty { t("Normal alarm", "一般鬧鐘") }, "normal"
                    )
                    break
                } else if (na.days.contains(cal.get(Calendar.DAY_OF_WEEK))) {
                    normalEntries += AlarmEntry(
                        baseId + d.coerceAtMost(9), na.id, t,
                        na.label.ifEmpty { t("Normal alarm", "一般鬧鐘") }, "normal"
                    )
                }
            }
        }

        val snoozes = data.scheduled.filter { it.isSnooze && it.triggerAt > now }
        val tests = data.scheduled.filter { it.kind == "test" && it.triggerAt > now }

        // Belt-and-braces: alarm ids are allocated in disjoint ranges per
        // source, but if a clash ever slipped through, drop the duplicate
        // instead of letting one PendingIntent silently replace another.
        val all = (workEntries + normalEntries + snoozes + tests)
            .distinctBy { it.id }
            .sortedBy { it.triggerAt }

        for (old in data.scheduled) AlarmScheduler.cancel(context, old)
        for (e in all) AlarmScheduler.schedule(context, e)

        val logText = when {
            errors.isNotEmpty() -> t("Failed: ", "失敗：") + errors.joinToString("；")
            all.isEmpty() -> t(
                "No alarms scheduled (read " + eventsRead + " events, matched " + matchedEvents + " shifts, " + offDays + " off days)",
                "排唔到鬧鐘（讀到 " + eventsRead + " 個事件、命中 " + matchedEvents + " 個更、休息日 " + offDays + "）"
            )
            else -> t(
                "Scheduled " + all.size + " alarms (read " + eventsRead + " events, matched " + matchedEvents + " shifts, " + offDays + " off days)",
                "排咗 " + all.size + " 粒鬧鐘（讀到 " + eventsRead + " 個事件、命中 " + matchedEvents + " 個更、休息日 " + offDays + "）"
            )
        }
        val newLogs = (listOf(SyncLog(System.currentTimeMillis(), logText)) + data.syncLogs).take(10)

        val newData = data.copy(
            scheduled = all,
            dismissedGroups = dismissed,
            dismissedAlarmIds = activeDeletedIds,
            dismissedAlarmMeta = activeDeletedMeta,
            dismissedAlarmTimes = activeDeletedTimes,
            syncLogs = newLogs
        )
        store.save(newData)

        // Keep the 3-minute pre-alarm broadcast scheduled for the next
        // alarm, and show the countdown notification if one is imminent.
        runCatching { CountdownNotificationManager.ensurePreAlarm(context) }
        runCatching { CountdownNotificationManager.checkAndShowCountdown(context) }

        SyncResult(all.size, matchedEvents, offDays, eventsRead, errors, all.firstOrNull())
    }

    fun schedulePeriodicSync(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "shift_sync", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}
