@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.shiftalarm.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shiftalarm.app.calendar.CalendarReader
import com.shiftalarm.app.calendar.CalInfo
import com.shiftalarm.app.core.Backup
import com.shiftalarm.app.core.L10n
import com.shiftalarm.app.core.Monetization
import com.shiftalarm.app.core.RuleEngine
import com.shiftalarm.app.core.t
import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.NormalAlarm
import com.shiftalarm.app.data.ShiftConfig
import com.shiftalarm.app.data.WorkProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- 地點設定檔 ----------

@Composable
fun ProfilesScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var editing by remember { mutableStateOf<WorkProfile?>(null) }
    val current = editing
    // Android back goes one level up (close the editor) instead of
    // exiting the app.
    BackHandler(enabled = current != null) { editing = null }
    if (current != null) {
        val isNew = data.profiles.none { it.id == current.id }
        ProfileEditScreen(
            initial = current,
            isNew = isNew,
            onDone = { updated ->
                if (updated != null) {
                    persistThenSync { d ->
                        d.copy(profiles = (d.profiles.filterNot { it.id == updated.id } + updated).sortedBy { it.name })
                    }
                }
                editing = null
            },
            onDelete = {
                persistThenSync { d -> d.copy(profiles = d.profiles.filterNot { it.id == current.id }) }
                editing = null
            }
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                t("Work Profiles", "工作地點設定檔"),
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                t(
                    "Event title contains the location keyword → profile matched; the shift code letter in the title picks the shift type; off-day keyword → no alarm.",
                    "事件標題含「地點關鍵字」→ 命中設定檔；再按標題嘅「更份代號」決定更種；標題含「休息日關鍵字」→ 唔排鬧鐘。"
                ),
                fontSize = 13.sp
            )
        }
        if (data.profiles.isEmpty()) {
            item { Text(t("(No profiles yet — tap below to add one)", "（未有設定檔，撳下面新增）"), fontSize = 14.sp) }
        }
        items(data.profiles) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().clickable { editing = p }.padding(16.dp)
                ) {
                    Text(
                        p.name.ifEmpty { t("(Unnamed)", "（未命名）") },
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        t("Keywords: ", "地點關鍵字：") + p.keywords.ifEmpty { t("(none)", "（未設）") } +
                            t("｜Off: ", "｜休息日：") + p.offKeyword.ifEmpty { t("(none)", "（無）") },
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    p.shifts.forEach { s ->
                        Text(
                            s.name + "（" + s.keyword.ifEmpty { t("no code", "無代號") } + "）" + s.wakeTime +
                                t(" wake·backup ", " 起·後備 ") + s.alarmCount + t("·every ", " 粒·每 ") + s.intervalMin + t(" min", " 分"),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        item {
            Button(onClick = { editing = WorkProfile() }, Modifier.fillMaxWidth()) {
                Text(t("＋ Add work profile", "＋ 新增地點設定檔"))
            }
        }
    }
}

@Composable
fun ProfileEditScreen(
    initial: WorkProfile,
    isNew: Boolean,
    onDone: (WorkProfile?) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var keywords by remember { mutableStateOf(initial.keywords) }
    var offKeyword by remember { mutableStateOf(initial.offKeyword) }
    var shifts by remember { mutableStateOf(initial.shifts) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                if (isNew) t("Add work profile", "新增地點設定檔")
                else t("Edit work profile", "編輯地點設定檔"),
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Location name (e.g. CMC)", "地點名稱（例：CMC）")) }
            )
        }
        item {
            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Location keywords (comma-separated, matched against event location + title, e.g. cmc)", "地點關鍵字（逗號分隔，比對事件地點＋標題，例：cmc）")) }
            )
        }
        item {
            OutlinedTextField(
                value = offKeyword,
                onValueChange = { offKeyword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Off-day keyword (title containing this = no alarm, e.g. off)", "休息日關鍵字（標題含此字＝唔排鬧鐘，例：off）")) }
            )
        }
        item {
            Text(
                t("Shift types (code + wake time each)", "更種設定（各自代號＋起身時間）"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        itemsIndexed(shifts) { idx, s ->
            ShiftEditor(
                cfg = s,
                onDelete = {
                    if (shifts.size > 1) shifts = shifts.filterIndexed { i, _ -> i != idx }
                },
                onChange = { updated ->
                    shifts = shifts.mapIndexed { i, old -> if (i == idx) updated else old }
                }
            )
        }
        item {
            OutlinedButton(
                onClick = { shifts = shifts + ShiftConfig(name = t("New shift", "新更種")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(t("＋ Add shift type", "＋ 新增更種"))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    onDone(WorkProfile(initial.id, name, keywords, offKeyword, shifts))
                }) { Text(t("Save", "儲存")) }
                OutlinedButton(onClick = { onDone(null) }) { Text(t("Cancel", "取消")) }
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text(t("Delete", "刪除"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ShiftEditor(cfg: ShiftConfig, onDelete: () -> Unit, onChange: (ShiftConfig) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = cfg.name,
                onValueChange = { onChange(cfg.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Shift name (e.g. Early)", "更種名稱（例：早更）")) }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfg.keyword,
                onValueChange = { onChange(cfg.copy(keyword = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Shift code (title containing this = this shift, e.g. a)", "更份代號（事件標題含此字＝用呢個更，例：a）")) }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Wake time", "起身時間"), Modifier.weight(1f))
                OutlinedButton(onClick = { showPicker = true }) { Text(cfg.wakeTime) }
            }
            Spacer(Modifier.height(8.dp))
            Stepper(t("Backup alarms (anti-snooze)", "後備鬧鐘數（防貪睡）"), cfg.alarmCount, 0, 9) { onChange(cfg.copy(alarmCount = it)) }
            Stepper(t("Backup interval (minutes)", "後備間隔（分鐘）"), cfg.intervalMin, 1, 30) { onChange(cfg.copy(intervalMin = it)) }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDelete) {
                Text(t("Delete this shift", "刪除此更"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showPicker) {
        val (h, m) = RuleEngine.parseTime(cfg.wakeTime)
        val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(cfg.copy(wakeTime = "%02d:%02d".format(state.hour, state.minute)))
                    showPicker = false
                }) { Text(t("OK", "確定")) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(t("Cancel", "取消")) }
            },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }) { Text("−") }
        Text("  $value  ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }) { Text("＋") }
    }
}

// ---------- 一般鬧鐘 ----------

fun dayName(dow: Int): String {
    val zh = mapOf(
        Calendar.SUNDAY to "日", Calendar.MONDAY to "一", Calendar.TUESDAY to "二",
        Calendar.WEDNESDAY to "三", Calendar.THURSDAY to "四", Calendar.FRIDAY to "五",
        Calendar.SATURDAY to "六"
    )
    val en = mapOf(
        Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat"
    )
    return if (L10n.lang == "zh") "週" + (zh[dow] ?: "") else (en[dow] ?: "")
}

@Composable
fun NormalAlarmsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    // Multi-select delete (like other alarm apps): long-press a row to enter
    // selection mode, tap rows to toggle selection, then delete all at once.
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selected = emptySet()
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(t("Alarms", "一般鬧鐘"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("Selected: ", "已選：") + selected.size,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    selectionMode = false
                    selected = emptySet()
                }) { Text(t("Cancel", "取消")) }
                Button(
                    onClick = {
                        persistThenSync { d ->
                            d.copy(normalAlarms = d.normalAlarms.filterNot { it.id in selected })
                        }
                        selectionMode = false
                        selected = emptySet()
                    },
                    enabled = selected.isNotEmpty()
                ) { Text(t("Delete", "刪除")) }
            }
        } else {
            Text(
                t(
                    "Custom alarms independent of the roster — one-off or weekly repeating.",
                    "獨立於更期嘅自訂鬧鐘，可設一次性或每週重複。"
                ),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.normalAlarms) { na ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selected = if (na.id in selected) selected - na.id else selected + na.id
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    selected = setOf(na.id)
                                }
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked = na.id in selected,
                                onCheckedChange = { on ->
                                    selected = if (on) selected + na.id else selected - na.id
                                }
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("%02d:%02d".format(na.hour, na.minute), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(na.label.ifEmpty { t("Alarm", "鬧鐘") }, fontSize = 14.sp)
                            Text(
                                if (na.days.isEmpty()) t("One-off", "一次性")
                                else t("Repeat: ", "重複：") + na.days.sortedBy { it }.map { dayName(it) }.joinToString("、"),
                                fontSize = 12.sp
                            )
                        }
                        if (!selectionMode) {
                            Switch(checked = na.enabled, onCheckedChange = { on ->
                                persistThenSync { d ->
                                    d.copy(normalAlarms = d.normalAlarms.map {
                                        if (it.id == na.id) it.copy(enabled = on) else it
                                    })
                                }
                            })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) { Text(t("＋ Add alarm", "＋ 新增鬧鐘")) }
    }
    if (showAdd) {
        AddNormalAlarmDialog { na ->
            showAdd = false
            if (na != null) {
                persistThenSync { d -> d.copy(normalAlarms = d.normalAlarms + na) }
            }
        }
    }
}

@Composable
fun AddNormalAlarmDialog(onDone: (NormalAlarm?) -> Unit) {
    val state = rememberTimePickerState(initialHour = 7, initialMinute = 0, is24Hour = true)
    var label by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf<Int>()) }
    val allDays = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(t("Add alarm", "新增一般鬧鐘")) },
        confirmButton = {
            TextButton(onClick = {
                onDone(
                    NormalAlarm(
                        id = System.currentTimeMillis(),
                        hour = state.hour,
                        minute = state.minute,
                        label = label,
                        days = days
                    )
                )
            }) { Text(t("OK", "確定")) }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text(t("Cancel", "取消")) }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Label", "標籤")) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    t(
                        "Repeat (none = one-off; scroll sideways for all 7 days)",
                        "重複（唔揀＝一次性，可左右滑動看全部七日）"
                    ),
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allDays.forEach { d ->
                        FilterChip(
                            selected = days.contains(d),
                            onClick = {
                                days = if (days.contains(d)) days - d else days + d
                            },
                            label = { Text(dayName(d)) }
                        )
                    }
                }
            }
        }
    )
}

// ---------- 設定 ----------

// ---------- 設定（分類目錄式，似手機設定） ----------

/** One browse-style category row: icon + title + subtitle + chevron. */
@Composable
private fun SettingsCategory(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(start = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Phone-settings-style sub-page: back button + title on top. */
@Composable
private fun SettingsSubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text(t("← Back", "← 返回")) }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
fun SettingsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    // Phone-settings style: browse categories, tap one to open its page.
    var page by remember { mutableStateOf("") }
    // Android back goes one level up (close the sub-page) instead of
    // exiting the app.
    BackHandler(enabled = page.isNotEmpty()) { page = "" }
    when (page) {
        "appearance" -> SettingsSubPage(t("Appearance & language", "外觀與語言"), { page = "" }) {
            SettingsAppearanceBody(data, persistThenSync)
        }
        "sources" -> SettingsSubPage(t("Roster sources", "更期來源"), { page = "" }) {
            SettingsSourcesBody(data, persistThenSync, openDeviceCals = { page = "devicecals" })
        }
        "devicecals" -> SettingsSubPage(t("Device calendars", "裝置日曆"), { page = "sources" }) {
            DeviceCalendarsScreen(data, persistThenSync)
        }
        "alarms" -> SettingsSubPage(t("Alarms", "鬧鐘"), { page = "" }) {
            SettingsAlarmsBody(data, persistThenSync)
        }
        "permissions" -> SettingsSubPage(t("Notifications & permissions", "通知與權限"), { page = "" }) {
            SettingsPermissionsBody()
        }
        "deleted" -> SettingsSubPage(t("Deleted alarms", "已刪除鬧鐘管理"), { page = "" }) {
            SettingsDeletedBody(data, persistThenSync)
        }
        "diagnostics" -> SettingsSubPage(t("Diagnostics", "診斷"), { page = "" }) {
            DiagnosticsScreen(data, persistThenSync)
        }
        "tutorial" -> TutorialScreen(onFinished = { page = "" })
        "about" -> SettingsSubPage(t("About", "關於"), { page = "" }) {
            SettingsAboutBody()
        }
        "backup" -> SettingsSubPage(t("Backup & restore", "備份與還原"), { page = "" }) {
            SettingsBackupBody(data, persistThenSync)
        }
        "ads" -> SettingsSubPage(t("Ads & purchase", "廣告與購買"), { page = "" }) {
            SettingsAdsBody(data, persistThenSync)
        }
        else -> {
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(t("Settings", "設定"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        t("Tap a category to open its settings.", "撳入分類即可修改設定。"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    SettingsCategory(
                        Icons.Filled.Palette,
                        t("Appearance & language", "外觀與語言"),
                        t("Dark mode, app language", "深淺色模式、語言")
                    ) { page = "appearance" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.CloudDownload,
                        t("Roster sources", "更期來源"),
                        t("iCal URL, device calendars", "iCal 網址、裝置日曆")
                    ) { page = "sources" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.Alarm,
                        t("Alarms", "鬧鐘"),
                        t("Snooze, schedule days, region alarm", "貪睡間隔、預排日數、地區鬧鐘")
                    ) { page = "alarms" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.NotificationsActive,
                        t("Notifications & permissions", "通知與權限"),
                        t("Battery, exact alarms, DND…", "電池優化、精確鬧鐘、勿擾等")
                    ) { page = "permissions" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.Delete,
                        t("Deleted alarms", "已刪除鬧鐘管理"),
                        t("Restore deleted roster alarms", "還原已刪除嘅更期鬧鐘")
                    ) { page = "deleted" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.BugReport,
                        t("Diagnostics", "診斷"),
                        t("Sync data, .ics import", "同步資料、匯入 .ics")
                    ) { page = "diagnostics" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.School,
                        t("Tutorial", "教學"),
                        t("How to use this app", "使用方法")
                    ) { page = "tutorial" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.CloudSync,
                        t("Backup & restore", "備份與還原"),
                        t("Save profiles & alarms to cloud / file", "將設定檔同鬧鐘備份到雲端／檔案")
                    ) { page = "backup" }
                }
                item {
                    SettingsCategory(
                        Icons.Filled.Info,
                        t("About", "關於"),
                        t("Version info", "版本資訊")
                    ) { page = "about" }
                }
                // Ads/purchase only appears while monetization is enabled.
                if (Monetization.ENABLED) {
                    item {
                        SettingsCategory(
                            Icons.Filled.ShoppingCart,
                            t("Ads & purchase", "廣告與購買"),
                            t("Remove ads", "移除廣告")
                        ) { page = "ads" }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAppearanceBody(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val s = data.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(t("Language", "語言"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en" to "English", "zh" to "中文").forEach { (value, label) ->
                if (s.language == value) {
                    Button(onClick = {
                        L10n.lang = value
                        persistThenSync { d -> d.copy(settings = d.settings.copy(language = value)) }
                    }) { Text(label) }
                } else {
                    OutlinedButton(onClick = {
                        L10n.lang = value
                        persistThenSync { d -> d.copy(settings = d.settings.copy(language = value)) }
                    }) { Text(label) }
                }
            }
        }
        Text(t("Appearance", "外觀"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val options = listOf(
            "system" to t("Follow system", "跟隨系統"),
            "light" to t("Light", "淺色"),
            "dark" to t("Dark", "深色")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                if (s.darkMode == value) {
                    Button(onClick = {
                        persistThenSync { d -> d.copy(settings = d.settings.copy(darkMode = value)) }
                    }) { Text(label) }
                } else {
                    OutlinedButton(onClick = {
                        persistThenSync { d -> d.copy(settings = d.settings.copy(darkMode = value)) }
                    }) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun SettingsSourcesBody(
    data: AppData,
    persistThenSync: ((AppData) -> AppData) -> Unit,
    openDeviceCals: () -> Unit
) {
    val s = data.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                t("iCal URL (roster source)", "iCal 網址（更期來源）"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            var icalUrl by remember(s.icalUrl) { mutableStateOf(s.icalUrl) }
            Text(
                t(
                    "Paste your Google Calendar secret iCal address (Settings → Import & export → Secret address). The app fetches it every hour. Priority: imported .ics file → device calendars → iCal URL. Shifts entered on the Calendar page always count.",
                    "貼上 Google Calendar 的「私人 iCal 網址」（齒輪設定 → 匯入和匯出／整合日曆 → 私人網址）。App 每小時自動抓取一次。優先次序：匯入嘅 .ics 檔案 → 裝置日曆 → iCal 網址。「日曆」分頁手動填嘅更一定會計。"
                ),
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = icalUrl,
                onValueChange = { icalUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Secret iCal address", "iCal 私人網址")) }
            )
            Button(onClick = {
                persistThenSync { d -> d.copy(settings = d.settings.copy(icalUrl = icalUrl.trim())) }
            }) { Text(t("Save & sync now", "儲存並立即同步")) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                t("Device calendars (roster source)", "裝置日曆（更期來源）"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text(
                t(
                    "Read shifts straight from the calendar apps on this phone (Google Calendar, Samsung Calendar, …). Pick which calendars to use. Used only when no .ics file is imported.",
                    "直接讀取手機上日曆 app（Google 日曆、Samsung 日曆等）嘅更期，揀選用邊個日曆。只喺冇匯入 .ics 檔案時先會用。"
                ),
                fontSize = 12.sp
            )
            OutlinedButton(onClick = openDeviceCals, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (s.deviceCalendarIds.isEmpty()) t("Select calendars", "揀選日曆")
                    else t("Select calendars (", "揀選日曆（已選 ") + s.deviceCalendarIds.size + t(" selected)", " 個）")
                )
            }
        }
    }
}

@Composable
private fun SettingsAlarmsBody(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val s = data.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField(t("Snooze interval (minutes)", "貪睡間隔（分鐘）"), s.snoozeMinutes) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(snoozeMinutes = v)) } }
            IntField(t("Days to schedule (min 7)", "預排日數（日，最少 7）"), s.lookaheadDays) { v -> persistThenSync { d -> d.copy(settings = d.settings.copy(lookaheadDays = v.coerceAtLeast(7))) } }
        }
        Text(
            t("Region alarm (for travel)", "地區鬧鐘（旅遊用）"),
            fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
        var showTzPicker by remember { mutableStateOf(false) }
        val tzLabel = if (s.alarmTimezone.isBlank()) t("Follow device", "跟隨裝置") else zoneLabel(s.alarmTimezone)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                t(
                    "Normal alarms ring at the selected region's local time. Pick Hong Kong and your 07:00 alarm still rings at 07:00 Hong Kong time while you're in Tokyo. Roster alarms follow the device timezone.",
                    "一般鬧鐘會喺選定地區嘅當地時間響。例如揀咗香港，去到東京旅行，07:00 鬧鐘照樣喺香港時間 07:00 響。更期鬧鐘則跟裝置時區。"
                ),
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = { showTzPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t("Alarm timezone: ", "鬧鐘時區：") + tzLabel) }
        }
        if (showTzPicker) {
            var tzQuery by remember { mutableStateOf("") }
            val candidates =
                (listOf("" to t("Follow device", "跟隨裝置")) +
                    WORLD_CITIES.map { it.zone to cityLabel(it) })
                    .filter { (zone, label) ->
                        zone.isEmpty() ||
                            label.contains(tzQuery, ignoreCase = true) ||
                            zone.contains(tzQuery, ignoreCase = true)
                    }
            AlertDialog(
                onDismissRequest = { showTzPicker = false },
                title = { Text(t("Pick alarm timezone", "揀鬧鐘時區")) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = tzQuery,
                            onValueChange = { tzQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("Search city / country", "搜尋城市／國家")) }
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.height(340.dp)) {
                            items(candidates, key = { it.first + it.second }) { (zone, label) ->
                                Text(
                                    label,
                                    fontSize = 15.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            persistThenSync { d ->
                                                d.copy(settings = d.settings.copy(alarmTimezone = zone))
                                            }
                                            showTzPicker = false
                                        }
                                        .padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTzPicker = false }) { Text(t("Close", "關閉")) }
                }
            )
        }
    }
}

@Composable
private fun SettingsPermissionRow(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Text(
                detail, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (ok) t("OK", "已開啟") else t("Off", "未開啟"),
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SettingsPermissionsBody() {
    val context = LocalContext.current
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            t(
                "Current status of every permission the alarm needs. Anything shown as Off has a button below it.",
                "呢度列出鬧鐘需要嘅全部權限同現時狀態。顯示「未開啟」嘅項目下面會有按鈕可以撳。"
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // --- Alarms & reminders (exact alarms) ---
        // Same as v1.8.1: the app uses the "Alarms & reminders" special
        // permission — the permission-needed page asks for it and the system
        // shows a per-app toggle under Special app access.
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val exactDetail = when {
            Build.VERSION.SDK_INT < 31 ->
                t("Not required on this Android version.", "呢個 Android 版本唔需要此權限。")
            exactOk -> t(
                "Granted via the \"Alarms & reminders\" special permission — alarms ring exactly, even in Doze.",
                "已透過「鬧鐘和提醒」特殊權限授予——鬧鐘準時響，Doze 休眠下都會響。"
            )
            else -> t(
                "Not granted — grant it on the permissions page shown at app start.",
                "未授予——喺 app 啟動時嘅權限頁面開啟。"
            )
        }
        SettingsPermissionRow(
            t("Alarms & reminders (exact alarms)", "鬧鐘和提醒（精確鬧鐘）"),
            exactOk, exactDetail
        )
        if (Build.VERSION.SDK_INT >= 31 && !exactOk) {
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:" + context.packageName)
                        )
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(t("Allow exact alarms", "允許精確鬧鐘")) }
        }

        // --- Notifications (Android 13+) ---
        if (Build.VERSION.SDK_INT >= 33) {
            val notifOk = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            SettingsPermissionRow(
                t("Notifications", "通知"), notifOk,
                t("Needed to show the ringing alarm notification.", "用嚟顯示響鬧鐘時嘅通知。")
            )
            if (!notifOk) {
                val launcher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { }
                Button(onClick = {
                    runCatching { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                }, modifier = Modifier.fillMaxWidth()) { Text(t("Allow notifications", "允許通知")) }
            }
        }

        // --- DND override ---
        val dndOk = nm.isNotificationPolicyAccessGranted
        SettingsPermissionRow(
            t("Do-Not-Disturb override", "勿擾模式繞過"), dndOk,
            t("Lets the alarm ring at full volume in Do-Not-Disturb.", "喺勿擾模式都可以全音量響鬧鐘。")
        )
        if (!dndOk) {
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(t("Allow Do-Not-Disturb override", "允許勿擾模式繞過")) }
        }

        // --- Full-screen notifications (Android 14+) ---
        if (Build.VERSION.SDK_INT >= 34) {
            val fsOk = nm.canUseFullScreenIntent()
            SettingsPermissionRow(
                t("Full-screen alarm notifications", "全螢幕鬧鐘通知"), fsOk,
                t("Shows the ringing screen even when the phone is locked.", "鎖屏時都可以彈出響鬧鐘畫面。")
            )
            if (!fsOk) {
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                android.net.Uri.parse("package:" + context.packageName)
                            )
                        )
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(t("Allow full-screen alarm notifications", "允許全螢幕鬧鐘通知")) }
            }
        }

        // --- Display over other apps ---
        val overlayOk = android.provider.Settings.canDrawOverlays(context)
        SettingsPermissionRow(
            t("Display over other apps", "在其他應用上層顯示"), overlayOk,
            t("Lets the ringing screen pop up over whatever app is open.", "用第二個 app 時都可以彈出響鬧鐘畫面。")
        )
        if (!overlayOk) {
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + context.packageName)
                        )
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(t("Allow display over other apps", "允許在其他應用上層顯示")) }
        }

        // --- Battery optimization exemption ---
        val battOk = pm.isIgnoringBatteryOptimizations(context.packageName)
        SettingsPermissionRow(
            t("Battery optimization exemption", "電池優化豁免"), battOk,
            t("Strongly recommended — stops the system from delaying alarms.", "強烈建議開啟——防止系統延遲鬧鐘。")
        )
        if (!battOk) {
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + context.packageName)
                        )
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(t("Exempt battery optimization", "豁免電池優化")) }
        }
    }
}

@Composable
private fun SettingsDeletedBody(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val now = System.currentTimeMillis()
    // Deleted alarms whose original fire time has passed are hidden —
    // e.g. a deleted 09:00 alarm disappears from this list at 09:01.
    // Legacy entries without a recorded time are still shown.
    val deletedList = data.dismissedAlarmMeta.entries.toList()
        .filter { (data.dismissedAlarmTimes[it.key] ?: Long.MAX_VALUE) > now }
    val deletedDateFmt = remember(L10n.lang) { L10n.newShortDateFmt() }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                t(
                    "Tap Restore to re-schedule a deleted alarm immediately (if its time hasn't passed). Deleting/dismissing only affects that single alarm. Deleted alarms whose original time has passed disappear from this list automatically.",
                    "撳「還原」會即刻重新排嗰粒鬧鐘（如果時間仲未過）。每次刪除／解除只會影響嗰一粒鬧鐘，同日其他鬧鐘唔會受影響。過咗原定時間嘅已刪鬧鐘會自動從呢度消失。"
                ),
                fontSize = 12.sp
            )
        }
        if (deletedList.isEmpty()) {
            item { Text(t("No deleted roster alarms.", "暫時未有已刪除嘅更期鬧鐘。"), fontSize = 12.sp) }
        }
        items(deletedList) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prefix the original fire date/time so same-label alarms
                    // on different days can be told apart.
                    val whenText = data.dismissedAlarmTimes[entry.key]
                        ?.let { deletedDateFmt.format(Date(it)) + " " } ?: ""
                    Text(whenText + entry.value, Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = {
                        persistThenSync { d ->
                            d.copy(
                                dismissedAlarmIds = d.dismissedAlarmIds - entry.key,
                                dismissedAlarmMeta = d.dismissedAlarmMeta - entry.key,
                                dismissedAlarmTimes = d.dismissedAlarmTimes - entry.key
                            )
                        }
                    }) { Text(t("Restore", "還原")) }
                }
            }
        }
        if (data.dismissedAlarmIds.isNotEmpty() || data.dismissedGroups.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = {
                        persistThenSync { d ->
                            d.copy(
                                dismissedAlarmIds = emptySet(),
                                dismissedAlarmMeta = emptyMap(),
                                dismissedAlarmTimes = emptyMap(),
                                dismissedGroups = emptyMap()
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Restore all deleted roster alarms", "還原全部已刪除嘅更期鬧鐘")) }
            }
        }
    }
}

/** Ads / purchase page — only reachable while monetization is enabled. */
@Composable
private fun SettingsAdsBody(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val context = LocalContext.current
    val s = data.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        if (s.adsRemoved) {
            Text(t("✓ Ads removed — thank you!", "✓ 已移除廣告，多謝支持！"), fontSize = 14.sp)
        } else {
            var purchaseMsg by remember { mutableStateOf<String?>(null) }
            var adUnit by remember(s.adUnitId) { mutableStateOf(s.adUnitId) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t(
                        "The ad banner only shows at the top — never full-page. A one-time purchase removes it forever.",
                        "廣告條只會顯示喺頂部，唔會彈出全頁廣告。一次性購買即可永久移除廣告。"
                    ),
                    fontSize = 12.sp
                )
                Button(onClick = {
                    val activity = context as? android.app.Activity
                    if (activity == null) {
                        purchaseMsg = t("Cannot start purchase", "無法啟動購買流程")
                        return@Button
                    }
                    com.shiftalarm.app.core.AdsBilling.purchase(activity) { ok, msg ->
                        purchaseMsg = msg
                        if (ok) persistThenSync { d ->
                            d.copy(settings = d.settings.copy(adsRemoved = true))
                        }
                    }
                }) { Text(t("Remove ads (one-time purchase)", "移除廣告（一次性購買）")) }
                purchaseMsg?.let { Text(it, fontSize = 12.sp) }
                OutlinedTextField(
                    value = adUnit,
                    onValueChange = { adUnit = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("AdMob ad unit id (optional, blank = test ads)", "AdMob 廣告單元 ID（選填，留空＝測試廣告）")) }
                )
                OutlinedButton(onClick = {
                    persistThenSync { d ->
                        d.copy(settings = d.settings.copy(adUnitId = adUnit.trim()))
                    }
                }) { Text(t("Save ad unit id", "儲存廣告單元 ID")) }
            }
        }
    }
}

@Composable
private fun SettingsBackupBody(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    // Export: user picks WHERE — the system picker can save straight into
    // Google Drive (or any cloud/file app), and the file then syncs to the
    // user's Drive automatically, ready to import on the new phone.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Backup.serialize(data).toByteArray())
                    } != null
                }.getOrDefault(false)
                message = if (ok)
                    t("✓ Backup saved. Keep the file (e.g. in Google Drive) and import it on your new phone.", "✓ 備份已儲存。留好個檔案（例如存入 Google Drive），換機時匯入返即可。")
                else
                    t("✗ Failed to write the backup file.", "✗ 寫入備份檔案失敗。")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                }.getOrNull()
                val merged = text?.let { Backup.restoreInto(data, it) }
                when {
                    text == null ->
                        message = t("✗ Failed to read the file.", "✗ 讀取檔案失敗。")
                    merged == null ->
                        message = t("✗ That file is not a valid backup.", "✗ 呢個檔案唔係有效嘅備份。")
                    else -> {
                        persistThenSync { merged }
                        message = t("✓ Backup restored — alarms rescheduled.", "✓ 備份已還原——鬧鐘已重新排程。")
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            t(
                "The backup contains your work profiles, normal alarms, manual shifts, world clocks and all settings. Two ways to keep them safe:",
                "備份包含你嘅地點設定檔、一般鬧鐘、手動更期、世界時鐘同全部設定。保護方法有兩種："
            ),
            fontSize = 13.sp
        )
        Text(
            t(
                "① Automatic: Android already backs this app's data up to your Google account (in Google Drive, under device backup) every day when idle — when you set up a new phone and restore from your account, Shiftlarm's data comes back with it.",
                "① 自動：Android 已經會每日喺開閒時自動將 app 資料備份到你嘅 Google 帳號（喺 Google Drive 嘅裝置備份入面）——新電話設定時由帳號還原，Shiftlarm 嘅資料會一齊返嚟。"
            ),
            fontSize = 13.sp
        )
        Text(
            t(
                "② Manual file: tap Export and choose Google Drive as the location — the .json backup file lives in your Drive and syncs to every device. On the new phone: install Shiftlarm → Settings → Backup & restore → Import.",
                "② 手動檔案：撳「匯出備份」並揀 Google Drive 做儲存位置——.json 備份檔會存喺你嘅 Drive 並同步到所有裝置。新電話：裝好 Shiftlarm → 設定 → 備份與還原 → 匯入。"
            ),
            fontSize = 13.sp
        )
        Button(
            onClick = { exportLauncher.launch(Backup.fileName()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(t("Export backup file (.json)", "匯出備份檔案（.json）")) }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(t("Import backup file", "匯入備份檔案")) }
        message?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsAboutBody() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("?")
        Text("Shiftlarm", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            t("Version ", "版本 ") + version,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            t(
                "Turns your work-roster calendar into alarm clocks automatically.",
                "自動將你嘅更期日曆變成鬧鐘。"
            ),
            fontSize = 13.sp
        )
    }
}

@Composable
fun IntField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            t.toIntOrNull()?.let { onChange(it.coerceIn(0, 48)) }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- 裝置日曆選擇 ----------

/** Pick which device calendars (CalendarProvider) hold the user's roster.
 *  Selection is stored against each calendar's STABLE server-side key, so
 *  renaming a calendar on desktop (or Android re-assigning its internal id)
 *  never breaks the link — the app always shows the current display name. */
@Composable
fun DeviceCalendarsScreen(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasPerm by remember {
        mutableStateOf(CalendarReader.hasPermission(context))
    }
    var cals by remember { mutableStateOf<List<CalInfo>>(emptyList()) }
    // Selection by stable key. Legacy id-only selections resolve to keys
    // once the calendar list loads, so existing users keep their picks.
    var selected by remember(data.settings.deviceCalendarNames) {
        mutableStateOf(data.settings.deviceCalendarNames.toMutableSet())
    }
    var resolvedLegacy by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPerm = granted }

    LaunchedEffect(hasPerm) {
        if (hasPerm) {
            val list = withContext(Dispatchers.IO) { CalendarReader.listCalendars(context) }
            cals = list
            if (!resolvedLegacy && data.settings.deviceCalendarNames.isEmpty()) {
                resolvedLegacy = true
                val legacyKeys = list
                    .filter { it.id in data.settings.deviceCalendarIds }
                    .map { it.key }
                if (legacyKeys.isNotEmpty()) selected = (selected + legacyKeys).toMutableSet()
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            t("Device calendars", "裝置日曆"),
            fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        if (!hasPerm) {
            Text(
                t(
                    "To read shifts from the calendar apps on this phone, allow calendar access first. The app only reads calendars you pick below.",
                    "要讀取手機日曆 app 嘅更期，請先允許日曆存取權限。App 只會讀取下面你揀嘅日曆。"
                ),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                Text(t("Allow calendar access", "允許日曆存取"))
            }
        } else {
            Text(
                t(
                    "Tick the calendars that hold your roster. Event titles still need to match your work profile keywords (e.g. \"cmc a\").",
                    "剔選載有你更期嘅日曆。事件標題仍要符合地點設定檔嘅關鍵字（例：cmc a）。"
                ),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(cals) { cal ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (cal.key in selected) (selected - cal.key).toMutableSet()
                            else (selected + cal.key).toMutableSet()
                        },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = cal.key in selected,
                            onCheckedChange = {
                                selected = if (it) (selected + cal.key).toMutableSet()
                                else (selected - cal.key).toMutableSet()
                            }
                        )
                        Column {
                            Text(cal.name.ifBlank { "(" + cal.id + ")" }, fontSize = 15.sp)
                            Text(
                                cal.account, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (cals.isEmpty()) {
                    item {
                        Text(
                            t("No calendars found on this device.", "呢部裝置搵唔到任何日曆。"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    // Save stable keys AND the current provider ids (kept for
                    // backward compatibility / migration on other installs).
                    val currentIds = cals.filter { it.key in selected }.map { it.id }.toSet()
                    persistThenSync { d ->
                        d.copy(settings = d.settings.copy(
                            deviceCalendarNames = selected.toSet(),
                            deviceCalendarIds = currentIds
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t("Save & sync now", "儲存並立即同步")) }
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        selected = mutableSetOf()
                        persistThenSync { d ->
                            d.copy(settings = d.settings.copy(
                                deviceCalendarIds = emptySet(),
                                deviceCalendarNames = emptySet()
                            ))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Stop using device calendars", "停用裝置日曆")) }
            }
        }
    }
}
