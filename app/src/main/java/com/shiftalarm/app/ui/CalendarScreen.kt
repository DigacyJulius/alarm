package com.shiftalarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftalarm.app.calendar.CalendarReader
import com.shiftalarm.app.calendar.CalEvent
import com.shiftalarm.app.calendar.IcalSource
import com.shiftalarm.app.core.L10n
import com.shiftalarm.app.core.RuleEngine
import com.shiftalarm.app.core.t
import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.ShiftEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * In-app shift calendar: for users without a calendar app, tap any day and
 * pick the work profile + shift (or off). Entries feed the alarm engine just
 * like roster events from iCal / device calendars.
 */
/** One shift shown on a day cell, from either source. */
private data class DayShift(
    val code: String,
    val off: Boolean,
    val manual: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }
    val monthFmt = remember(L10n.lang) {
        DateTimeFormatter.ofPattern("MMMM yyyy", L10n.locale)
    }

    // ---- Synced shifts (ics import / device calendars / iCal URL), matched
    // ---- against the work profiles so the calendar shows what the alarm
    // ---- engine sees. Fetched per visible month.
    var syncedEvents by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(
        month, data.icalEvents, data.settings.icalUrl,
        data.settings.deviceCalendarIds, data.settings.deviceCalendarNames,
        data.profiles
    ) {
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        syncedEvents = withContext(Dispatchers.IO) {
            when {
                data.icalEvents.isNotEmpty() ->
                    data.icalEvents.filter { it.begin >= from && it.begin <= to }
                data.settings.deviceCalendarIds.isNotEmpty() ||
                    data.settings.deviceCalendarNames.isNotEmpty() ->
                    runCatching {
                        CalendarReader.queryEvents(
                            ctx, from, to,
                            data.settings.deviceCalendarIds,
                            data.settings.deviceCalendarNames
                        )
                    }.getOrDefault(emptyList())
                data.settings.icalUrl.isNotBlank() ->
                    runCatching {
                        IcalSource.fetchEventsFromUrl(data.settings.icalUrl, from, to)
                    }.getOrDefault(emptyList())
                else -> emptyList()
            }
        }
    }

    /** Shifts to show on a day: manual entries + synced matches. */
    fun shiftsFor(date: LocalDate): List<DayShift> {
        val result = mutableListOf<DayShift>()
        data.manualShifts.filter { it.date == date.toString() }.forEach { e ->
            val p = data.profiles.firstOrNull { it.id == e.profileId }
            result += DayShift(
                code = if (e.off) t("off", "休")
                else p?.shifts?.firstOrNull { it.name == e.shiftName }?.keyword
                    ?.ifBlank { e.shiftName.take(4) } ?: e.shiftName.take(4),
                off = e.off,
                manual = true
            )
        }
        syncedEvents.filter {
            it.begin >= date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() &&
                it.begin < date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.forEach { ev ->
            // Mirror the alarm engine exactly: only events that match a
            // profile keyword count, off keyword → off day.
            val p = RuleEngine.matchProfile(ev, data.profiles) ?: return@forEach
            if (RuleEngine.isOffDay(ev, p)) {
                result += DayShift(t("off", "休"), true, manual = false)
            } else {
                val cfg = RuleEngine.pickShift(ev, p, data.settings)
                if (cfg != null) {
                    result += DayShift(
                        code = cfg.keyword.ifBlank { cfg.name.take(4) },
                        off = false,
                        manual = false
                    )
                }
            }
        }
        return result.distinctBy { it.code + "#" + it.manual }.take(3)
    }

    fun entryFor(date: LocalDate, profileId: Long): ShiftEntry? =
        data.manualShifts.firstOrNull { it.date == date.toString() && it.profileId == profileId }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Month header with navigation.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                month.format(monthFmt),
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        TextButton(
            onClick = { month = YearMonth.from(today) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(t("Jump to today", "回到今天")) }
        Spacer(Modifier.height(4.dp))

        // Weekday header.
        Row(Modifier.fillMaxWidth()) {
            // Week starts on Sunday to match dayName().
            val names = listOf(
                dayName(java.util.Calendar.SUNDAY), dayName(java.util.Calendar.MONDAY),
                dayName(java.util.Calendar.TUESDAY), dayName(java.util.Calendar.WEDNESDAY),
                dayName(java.util.Calendar.THURSDAY), dayName(java.util.Calendar.FRIDAY),
                dayName(java.util.Calendar.SATURDAY)
            )
            names.forEach {
                Text(
                    it, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Day grid.
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value % 7 // Sunday=0
        val daysInMonth = month.lengthOfMonth()
        val cells = List(leading) { null } + (1..daysInMonth).map { month.atDay(it) }
        val weeks = (cells + List((7 - cells.size % 7) % 7) { null }).chunked(7)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            weeks.forEach { week ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    when (date) {
                                        selected -> MaterialTheme.colorScheme.primaryContainer
                                        today -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    CircleShape
                                )
                                .clickable { date?.let { selected = it } }
                        ) {
                            val entries = date?.let { d -> shiftsFor(d) } ?: emptyList()
                            Column(
                                Modifier.fillMaxSize().padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    date?.dayOfMonth?.toString() ?: "",
                                    fontSize = 14.sp,
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal
                                )
                                entries.take(2).forEach { e ->
                                    Text(
                                        e.code.take(5), fontSize = 9.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (e.manual) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            t(
                "Tap a day to enter your shift. Codes in the theme colour are your manual entries; codes in the accent colour are shifts matched from your synced source (ics / device calendar / iCal URL). Entries here always count — even without iCal or device calendars.",
                "撳日期填更期。藍色代碼係你手動填嘅更；另一種顏色嘅代碼係由同步來源（ics / 裝置日曆 / iCal 網址）配對到嘅更。呢度填嘅更一定會計入鬧鐘——就算冇 iCal 或裝置日曆都用得。"
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Day editor: pick the shift (or off) for each work profile.
    selected?.let { date ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Text(
                    date.format(DateTimeFormatter.ofPattern(
                        if (L10n.lang == "zh") "M月d日 (E)" else "EEE, MMM d", L10n.locale
                    ))
                )
            },
            text = {
                val timeFmt = SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayEvents = syncedEvents.filter { it.begin >= dayStart && it.begin < dayEnd }
                if (data.profiles.isEmpty()) {
                    Column {
                        Text(
                            t(
                                "No work profiles yet. Create one on the Profile page first (e.g. name CMC), then pick shifts here.",
                                "仲未有地點設定檔。先去「設定檔」新增（例：名稱 CMC），再返嚟揀更份。"
                            )
                        )
                        if (dayEvents.isNotEmpty()) {
                            Text(
                                t("Events on this day (from your synced source):", "呢日嘅事件（來自同步來源）："),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            dayEvents.forEach { ev ->
                                Text(
                                    timeFmt.format(Date(ev.begin)) +
                                        (if (ev.end > ev.begin) "–" + timeFmt.format(Date(ev.end)) else "") +
                                        "  " + ev.title,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        data.profiles.forEach { profile ->
                            val entry = entryFor(date, profile.id)
                            // What the synced source says for this profile on this date.
                            val synced = dayEvents.firstOrNull {
                                RuleEngine.matchProfile(it, data.profiles)?.id == profile.id
                            }
                            if (synced != null) {
                                if (RuleEngine.isOffDay(synced, profile)) {
                                    Text(
                                        t("Synced: off day", "同步：休息日"),
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else {
                                    val cfg = RuleEngine.pickShift(synced, profile, data.settings)
                                    Text(
                                        t("Synced: ", "同步：") +
                                            (cfg?.name ?: t("(no shift matched)", "（未配對到更）")) +
                                            " " + timeFmt.format(Date(synced.begin)) +
                                            (if (synced.end > synced.begin) "–" + timeFmt.format(Date(synced.end)) else ""),
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text(
                                    if (entry != null) t("→ overridden by your manual setting below", "→ 已被下面嘅手動設定覆蓋")
                                    else t("→ pick a shift below to override the synced one", "→ 揀下面嘅更即可覆蓋同步嗰個"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            Text(
                                profile.name.ifBlank { t("(profile)", "（設定檔）") },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            ShiftChips(
                                profile = profile,
                                selectedShift = if (entry != null && !entry.off) entry.shiftName else null,
                                selectedOff = entry?.off == true,
                                onPick = { shiftName, off ->
                                    persistThenSync { d ->
                                        val others = d.manualShifts.filterNot {
                                            it.date == date.toString() && it.profileId == profile.id
                                        }
                                        val keep = if (shiftName == null && !off) emptyList()
                                        else listOf(
                                            ShiftEntry(
                                                date = date.toString(),
                                                profileId = profile.id,
                                                shiftName = shiftName ?: "",
                                                off = off
                                            )
                                        )
                                        d.copy(manualShifts = others + keep)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text(t("Done", "完成")) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftChips(
    profile: com.shiftalarm.app.data.WorkProfile,
    selectedShift: String?,
    selectedOff: Boolean,
    onPick: (shiftName: String?, off: Boolean) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        profile.shifts.forEach { shift ->
            val isSelected = shift.name == selectedShift
            FilterChip(
                selected = isSelected,
                onClick = {
                    // Tapping the already-selected chip clears the day.
                    onPick(if (isSelected) null else shift.name, false)
                },
                label = {
                    Text(
                        shift.name + if (shift.keyword.isBlank()) "" else " (${shift.keyword})"
                    )
                }
            )
        }
        FilterChip(
            selected = selectedOff,
            onClick = { onPick(null, !selectedOff) },
            label = { Text(t("Off", "休息")) }
        )
    }
}
