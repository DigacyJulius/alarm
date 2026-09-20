package com.shiftalarm.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shiftalarm.app.calendar.CalendarReader
import com.shiftalarm.app.calendar.EventPreview
import com.shiftalarm.app.calendar.IcalSource
import com.shiftalarm.app.core.L10n
import com.shiftalarm.app.core.t
import com.shiftalarm.app.data.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previews by remember { mutableStateOf<List<EventPreview>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var icalStatus by remember { mutableStateOf<String?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var icalUrl by remember(data.settings.icalUrl) { mutableStateOf(data.settings.icalUrl) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val evs = withContext(Dispatchers.IO) {
                        IcalSource.parseFromUri(context, uri)
                    }
                    if (evs.isEmpty()) {
                        importStatus = t("Imported, but no events found in the file", "匯入成功，但檔案入面搵唔到任何事件")
                    } else {
                        importStatus = t("✓ Imported ", "✓ 已匯入 ") + evs.size + t(" events (any date) — used for alarms right away", " 個事件（任何日期），即刻用嚟排鬧鐘")
                        persistThenSync { d -> d.copy(icalEvents = evs) }
                    }
                } catch (e: Exception) {
                    importStatus = t("✗ Import failed: ", "✗ 匯入失敗：") + (e.message ?: e.toString())
                }
            }
        }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayFmt = remember(L10n.lang) {
        SimpleDateFormat(if (L10n.lang == "zh") "M月d日 (E)" else "EEE, MMM d", L10n.locale)
    }
    val shortDayFmt = remember(L10n.lang) {
        SimpleDateFormat(if (L10n.lang == "zh") "M月d日" else "MMM d", L10n.locale)
    }
    val logTimeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val nowMs = System.currentTimeMillis()
    val from = nowMs - 12L * 3600_000L
    val to = nowMs + maxOf(data.settings.lookaheadDays, 7).toLong() * 86400_000L

    LaunchedEffect(Unit) {
        if (data.icalEvents.isNotEmpty()) {
            previews = data.icalEvents.filter { it.begin >= from && it.begin <= to }
                .sortedBy { it.begin }
                .take(200)
                .map { EventPreview(it.title, it.begin, it.calendarId) }
        } else if (data.settings.deviceCalendarIds.isNotEmpty() || data.settings.deviceCalendarNames.isNotEmpty()) {
            try {
                val evs = withContext(Dispatchers.IO) {
                    CalendarReader.queryEvents(
                        context, from, to,
                        data.settings.deviceCalendarIds,
                        data.settings.deviceCalendarNames
                    )
                }.sortedBy { it.begin }
                icalStatus = t("✓ Device calendars read OK: ", "✓ 裝置日曆讀取成功：窗口內 ") + evs.size + t(" events in window", " 個事件")
                previews = evs.take(200).map { EventPreview(it.title, it.begin, it.calendarId) }
            } catch (e: Exception) {
                icalStatus = t("✗ Device calendar read failed (permission?) — ", "✗ 裝置日曆讀取失敗（權限？）——") + (e.message ?: e.toString())
            }
        } else if (data.settings.icalUrl.isNotBlank()) {
            try {
                val evs = withContext(Dispatchers.IO) {
                    IcalSource.fetchEventsFromUrl(data.settings.icalUrl, from, to)
                }.sortedBy { it.begin }
                icalStatus = t("✓ iCal fetch OK: ", "✓ iCal 網址抓取成功：窗口內 ") + evs.size + t(" events in window", " 個事件")
                previews = evs.take(200).map { EventPreview(it.title, it.begin, it.calendarId) }
            } catch (e: Exception) {
                icalStatus = t("✗ iCal fetch failed: ", "✗ iCal 網址抓取失敗：") + (e.message ?: e.toString())
            }
        }
        loading = false
    }

    val byDay = previews.groupBy { dayFmt.format(Date(it.begin)) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text(t("Diagnostics / Schedule", "診斷 / 日程"), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item { Text(t("Shows the iCal data the app actually reads — only what appears here is used to schedule alarms.", "顯示 App 實際讀到嘅 iCal 數據——呢度有嘅嘢，先會被用嚟排鬧鐘。"), fontSize = 13.sp) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t("Notification permission", "通知權限"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val hasNotif = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    Text(
                        if (hasNotif) t("✓ Notification permission granted", "✓ 通知權限已授予")
                        else t("✗ Notification permission not granted (alarm ringing may not show)", "✗ 通知權限未授予（鬧鐘響鈴可能唔會顯示）"),
                        fontSize = 13.sp,
                        color = if (hasNotif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t("iCal / Import", "iCal / 匯入"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val source = when {
                        data.icalEvents.isNotEmpty() -> {
                            val min = data.icalEvents.minOfOrNull { it.begin }
                            val max = data.icalEvents.maxOfOrNull { it.begin }
                            val range = if (min != null && max != null) {
                                t(" (covers ", "（涵蓋 ") + shortDayFmt.format(Date(min)) + " → " + shortDayFmt.format(Date(max)) + t(")", "）")
                            } else ""
                            t("Current source: imported .ics file (", "現時來源：已匯入嘅 .ics 檔案（") + data.icalEvents.size + t(" events)", " 個事件）") + range
                        }
                        data.settings.deviceCalendarIds.isNotEmpty() || data.settings.deviceCalendarNames.isNotEmpty() -> t("Current source: device calendars (", "現時來源：裝置日曆（已選 ") + maxOf(data.settings.deviceCalendarIds.size, data.settings.deviceCalendarNames.size) + t(" selected)", " 個）")
                        data.settings.icalUrl.isNotBlank() -> t("Current source: iCal URL", "現時來源：iCal 網址")
                        else -> t("Current source: (none — use a device calendar, an iCal URL, an .ics import, or the Calendar page)", "現時來源：（未設定——可用裝置日曆、iCal 網址、匯入檔案，或「日曆」分頁手動填更）")
                    }
                    Text(source, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        filePicker.launch(arrayOf("text/calendar", "application/ics", "application/octet-stream", "*/*"))
                    }) { Text(t("📂 Import .ics file", "📂 匯入 .ics 檔案")) }
                    if (data.icalEvents.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = {
                            importStatus = t("Imported data cleared — using iCal URL (if set)", "已清除匯入資料，改用 iCal 網址（如有設定）")
                            persistThenSync { d -> d.copy(icalEvents = emptyList()) }
                        }) { Text(t("Clear imported data", "清除匯入資料")) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = icalUrl,
                        onValueChange = { icalUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("Secret iCal address", "iCal 私人網址")) }
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = {
                        persistThenSync { d -> d.copy(settings = d.settings.copy(icalUrl = icalUrl.trim())) }
                    }) { Text(t("Save iCal URL & sync", "儲存 iCal 網址並同步")) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t(
                            "For import files: Google Calendar web → Settings → Import & export → Export → download .ics → copy to phone. An imported file takes priority over the iCal URL.",
                            "匯入用檔案：Google Calendar 網頁版 → 齒輪設定 → 匯入和匯出 → 匯出 → 下載 .ics → 傳去手機。匯入咗嘅更期優先過 iCal 網址。"
                        ),
                        fontSize = 12.sp
                    )
                    icalStatus?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, fontSize = 13.sp, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    importStatus?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, fontSize = 13.sp, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item {
            Text(
                t("Schedule preview (grouped by day)・fetch window: ", "日程預覽（按日分組）・抓取視窗：") + shortDayFmt.format(Date(from)) + " → " + shortDayFmt.format(Date(to)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (loading) {
            item { Text(t("Loading…", "載入中…"), fontSize = 13.sp) }
        }
        if (previews.isEmpty() && !loading) {
            item {
                Text(
                    t(
                        "No events — paste an iCal URL in Settings, or import an .ics file here.",
                        "未有任何事件——請喺「設定」貼上 iCal 網址，或喺呢度匯入 .ics 檔案。"
                    ),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.error
                )
            }
        }
        byDay.forEach { (day, events) ->
            item {
                Spacer(Modifier.height(4.dp))
                Text(day, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            items(events) { ev ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                ev.title.ifEmpty { t("(no title)", "（無標題）") },
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(timeFmt.format(Date(ev.begin)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item { Text(t("Sync log (last 10)", "同步記錄（最近 10 次）"), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (data.syncLogs.isEmpty()) {
            item { Text(t("(No logs yet — tap “Sync now” on the Home page)", "（暫無記錄——返首頁撳一次「立即同步」）"), fontSize = 13.sp) }
        }
        items(data.syncLogs) { log ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(logTimeFmt.format(Date(log.time)), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(log.text, fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
