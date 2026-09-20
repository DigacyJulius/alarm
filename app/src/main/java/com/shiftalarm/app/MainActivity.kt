package com.shiftalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiftalarm.app.core.AlarmScheduler
import com.shiftalarm.app.core.AlarmWatchdogWorker
import com.shiftalarm.app.core.CountdownNotificationManager
import com.shiftalarm.app.core.ExactAlarmPermission
import com.shiftalarm.app.core.L10n
import com.shiftalarm.app.core.Monetization
import com.shiftalarm.app.core.PermissionGateScreen
import com.shiftalarm.app.core.PermissionNudgeEvent
import com.shiftalarm.app.core.SyncEngine
import com.shiftalarm.app.core.t
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.AppData
import com.shiftalarm.app.data.Store
import com.shiftalarm.app.ui.CalendarScreen
import com.shiftalarm.app.ui.DiagnosticsScreen
import com.shiftalarm.app.ui.NormalAlarmsScreen
import com.shiftalarm.app.ui.ProfilesScreen
import com.shiftalarm.app.ui.SettingsScreen
import com.shiftalarm.app.ui.StopwatchView
import com.shiftalarm.app.ui.TimerView
import com.shiftalarm.app.ui.ToolTabs
import com.shiftalarm.app.ui.TutorialScreen
import com.shiftalarm.app.ui.WorldClockView
import com.shiftalarm.app.ui.zoneLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LightColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFF6B5778),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    secondary = Color(0xFFBBC7DB),
    tertiary = Color(0xFFD6BEE4),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    error = Color(0xFFFFB4AB)
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncEngine.schedulePeriodicSync(this)

        // Initialize AdMob off the main thread (Google recommends this).
        // Hidden behind the monetization switch while ads are disabled.
        if (Monetization.ENABLED) {
            Thread {
                runCatching { com.google.android.gms.ads.MobileAds.initialize(this) }
            }.start()
        }

        // The old 24/7 standby foreground service is gone (user request:
        // no persistent notification). Its job is now done by a
        // notification-free WorkManager watchdog that re-registers all
        // alarms every ~15 minutes.
        AlarmWatchdogWorker.ensureScheduled(this)
        AlarmWatchdogWorker.runNow(this)
        // Clean up the leftover standby notification channel, if any.
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.deleteNotificationChannel("alarm_standby")
        }

        // Check for upcoming alarms and show countdown notification if needed
        lifecycleScope.launch {
            CountdownNotificationManager.checkAndShowCountdown(this@MainActivity)
        }

        // Migration: make sure the stored lookahead is at least 7 days even
        // if an older build saved a smaller value.
        lifecycleScope.launch {
            val data = Store(this@MainActivity).data.first()
            if (data.settings.lookaheadDays < 7) {
                Store(this@MainActivity).save(
                    data.copy(settings = data.settings.copy(lookaheadDays = 7))
                )
            }
        }

        setContent {
            var permissionsGranted by remember { mutableStateOf(false) }

            // Completes the permission-nudge chain pushed by the user: when
            // AlarmScheduler fails to set an exact alarm because the
            // "Alarms & reminders" permission is missing/revoked, it
            // broadcasts PermissionNudgeEvent — show a dialog directing the
            // user to the system toggle.
            var showNudge by remember { mutableStateOf(false) }
            val activity = this
            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) { showNudge = true }
                }
                androidx.core.content.ContextCompat.registerReceiver(
                    activity,
                    receiver,
                    IntentFilter(PermissionNudgeEvent.ACTION),
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
                onDispose { activity.unregisterReceiver(receiver) }
            }

            if (showNudge) {
                ExactAlarmPermission.PermissionNudgeDialog(
                    onDismiss = { showNudge = false },
                    onOpenSettings = { ExactAlarmPermission.openSettings(activity) }
                )
            }

            if (!permissionsGranted) {
                // Notification and special alarm permissions are all requested
                // from the gate screen.
                PermissionGateScreen(onAllGranted = { permissionsGranted = true })
            } else {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val scope = rememberCoroutineScope()
    val data by remember { store.data }.collectAsStateWithLifecycle(initialValue = AppData())
    var tab by remember { mutableIntStateOf(0) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showTutorial by remember { mutableStateOf(false) }

    // Sync the app language from stored settings.
    LaunchedEffect(data.settings.language) {
        L10n.lang = data.settings.language.ifEmpty { "en" }
    }
    // Show the tutorial on first launch ONLY. Wait for the stored data to
    // load first — the reactive `data` starts as the default AppData()
    // (tutorialDone=false) before DataStore finishes reading, which used to
    // flash the tutorial on EVERY launch even after completion.
    LaunchedEffect(Unit) {
        val stored = store.data.first()
        if (!stored.settings.tutorialDone) showTutorial = true
    }

    // FLAT swipeable pager with wrap-around. Every tool sub-page is a
    // TOP-LEVEL page, so swiping between tools is indistinguishable from
    // swiping between main tabs (no nested pager = no gesture threshold).
    //   page 0 = Home | 1-4 = Tools (Alarms, Clock, Stopwatch, Timer)
    //   5 = Profile | 6 = Calendar | 7 = Settings
    val pageCount = 8
    val anchor = 4000 // divisible by pageCount
    val pagerState = rememberPagerState(initialPage = anchor) { anchor * 2 }
    var lastToolPage by remember { mutableIntStateOf(1) }
    // Timer wheel selection hoisted here so it survives page switches.
    var timerSel by remember { mutableStateOf(Triple(0, 5, 0)) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { p ->
            val m = ((p % pageCount) + pageCount) % pageCount
            if (m in 1..4) lastToolPage = m
            tab = when {
                m == 0 -> 0
                m in 1..4 -> 1
                else -> m - 3
            }
        }
    }

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (data.settings.darkMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    fun doSync() {
        scope.launch {
            syncMessage = t("Syncing…", "同步中…")
            val r = SyncEngine.sync(context)
            syncMessage = when {
                r.errors.isNotEmpty() ->
                    t("Sync failed: ", "同步失敗：") + r.errors.joinToString("；")
                data.profiles.isEmpty() ->
                    t(
                        "Synced, but no alarms scheduled: no work profiles yet. Go to Profile → add one (e.g. name CMC, keyword cmc), save, then sync again.",
                        "同步完成，但排唔到任何鬧鐘：仲未有地點設定檔。去「設定檔」→ 新增（例：名稱 CMC、地點關鍵字 cmc），儲存後再撳同步。"
                    )
                data.settings.icalUrl.isBlank() && data.icalEvents.isEmpty() &&
                    data.settings.deviceCalendarIds.isEmpty() &&
                    data.settings.deviceCalendarNames.isEmpty() && data.manualShifts.isEmpty() ->
                    t(
                        "Synced, but no roster source set. Paste an iCal URL, pick device calendars, import an .ics file (Diagnostics), or enter shifts by hand on the Calendar page.",
                        "同步完成，但未設定更期來源。可以貼 iCal 網址、揀裝置日曆、喺「診斷」匯入 .ics 檔案，或直接喺「日曆」分頁手動填更。"
                    )
                r.eventsRead == 0 ->
                    t(
                        "Synced, but 0 events read. Check: ① is the iCal URL correct? ② are the shifts within the next ",
                        "同步完成，但讀到 0 個事件。檢查：① iCal 網址有冇填對？② 更期係咪喺未來 "
                    ) + maxOf(data.settings.lookaheadDays, 7) +
                        t(" days? (see Diagnostics for details)", " 日內？（去「診斷」睇詳情）")
                r.matchedEvents == 0 && r.offDays == 0 ->
                    t(
                        "Synced: read " + r.eventsRead + " events but none matched a profile. Check that event titles (e.g. cmc a) match your profile keywords. (see Diagnostics for event titles)",
                        "同步完成：讀到 " + r.eventsRead + " 個事件，但冇一個命中設定檔。檢查事件標題（例：cmc a）同設定檔嘅「地點關鍵字」係咪一致。（去「診斷」睇事件標題）"
                    )
                r.total == 0 ->
                    t(
                        "Synced: matched " + r.matchedEvents + " shifts, " + r.offDays + " off days, but all wake-up times have passed.",
                        "同步完成：命中 " + r.matchedEvents + " 個更、" + r.offDays + " 個休息日，但全部起身時間已過。"
                    )
                else ->
                    t(
                        "Synced: matched " + r.matchedEvents + " shifts, skipped " + r.offDays + " off days — scheduled " + r.total + " alarms",
                        "同步完成：命中 " + r.matchedEvents + " 個更、跳過 " + r.offDays + " 個休息日，共排 " + r.total + " 粒鬧鐘"
                    )
            }
        }
    }

    LaunchedEffect(Unit) { doSync() }

    fun persistThenSync(transform: (AppData) -> AppData) {
        scope.launch {
            val current = store.data.first()
            store.save(transform(current))
            SyncEngine.sync(context)
        }
    }

    fun deleteAlarm(entry: AlarmEntry) {
        scope.launch {
            val current = store.data.first()
            when (entry.kind) {
                "work" -> {
                    // Per-alarm deletion: only the tapped alarm is cancelled
                    // and blocked from being rebuilt by the next sync. The
                    // other alarms of that day (backups / other shifts) stay
                    // untouched.
                    AlarmScheduler.cancel(context, entry)
                    val kept = current.scheduled.filterNot { it.id == entry.id }
                    store.save(
                        current.copy(
                            scheduled = kept,
                            dismissedAlarmIds = current.dismissedAlarmIds + entry.id,
                            dismissedAlarmMeta = current.dismissedAlarmMeta + (entry.id to entry.label),
                            dismissedAlarmTimes = current.dismissedAlarmTimes + (entry.id to entry.triggerAt)
                        )
                    )
                }
                "normal" -> {
                    val related = current.scheduled.filter { it.kind == "normal" && it.groupId == entry.groupId }
                    related.forEach { AlarmScheduler.cancel(context, it) }
                    val kept = current.scheduled.filterNot { related.contains(it) }
                    store.save(
                        current.copy(
                            scheduled = kept,
                            normalAlarms = current.normalAlarms.filterNot { it.id == entry.groupId }
                        )
                    )
                }
                else -> {
                    AlarmScheduler.cancel(context, entry)
                    val kept = current.scheduled.filterNot { it.id == entry.id }
                    store.save(current.copy(scheduled = kept))
                }
            }
            // Make the countdown / standby notifications reflect the deletion
            runCatching { CountdownNotificationManager.checkAndShowCountdown(context) }
        }
    }

    /**
     * Bulk delete for the Home multi-select: ONE read + ONE save. Calling
     * deleteAlarm() in a loop races (each call reads the data before the
     * previous save lands), so only the last deletion survived.
     */
    fun deleteAlarms(entries: List<AlarmEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            val current = store.data.first()
            val workIds = entries.filter { it.kind != "normal" }.map { it.id }.toSet()
            val normalGroupIds = entries.filter { it.kind == "normal" }.map { it.groupId }.toSet()
            // Cancel every affected alarm with the system scheduler in one pass.
            current.scheduled
                .filter { it.id in workIds || (it.kind == "normal" && it.groupId in normalGroupIds) }
                .forEach { AlarmScheduler.cancel(context, it) }
            val removedNormalIds = current.scheduled
                .filter { it.kind == "normal" && it.groupId in normalGroupIds }
                .map { it.id }
            val removedIds = workIds + removedNormalIds
            val kept = current.scheduled.filterNot { it.id in removedIds }
            store.save(
                current.copy(
                    scheduled = kept,
                    dismissedAlarmIds = current.dismissedAlarmIds + removedIds,
                    dismissedAlarmMeta = current.dismissedAlarmMeta +
                        entries.filter { it.kind != "normal" }.associate { it.id to it.label },
                    dismissedAlarmTimes = current.dismissedAlarmTimes +
                        entries.filter { it.kind != "normal" }.associate { it.id to it.triggerAt },
                    normalAlarms = current.normalAlarms.filterNot { it.id in normalGroupIds }
                )
            )
            runCatching { CountdownNotificationManager.checkAndShowCountdown(context) }
        }
    }

    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    // Five tabs, single-line labels that fit every phone.
                    val labels = listOf(
                        t("Home", "首頁") to Icons.Filled.Home,
                        t("Tools", "工具") to Icons.Filled.Schedule,
                        t("Profile", "設定檔") to Icons.Filled.Place,
                        t("Calendar", "日曆") to Icons.Filled.DateRange,
                        t("Settings", "設定") to Icons.Filled.Settings
                    )
                    labels.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = {
                                // Tools returns to the last-used tool page.
                                val target = when (i) {
                                    0 -> 0
                                    1 -> lastToolPage
                                    else -> i + 3
                                }
                                val cur = ((pagerState.currentPage % pageCount) + pageCount) % pageCount
                                var d = (target - cur + pageCount) % pageCount
                                if (d > pageCount / 2) d -= pageCount
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + d)
                                }
                            },
                            icon = { Icon(icon, null) },
                            label = { Text(label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                // Top banner ad (hidden once the remove-ads purchase is made,
                // and while monetization is globally disabled).
                if (Monetization.ENABLED && !data.settings.adsRemoved) {
                    AdBanner(adUnitId = data.settings.adUnitId)
                }
                // Tools sub-tab bar, hoisted ABOVE the pager: during a swipe
                // you see ONE fixed bar with only the content sliding. The
                // indicator follows the gesture (targetPage) so it feels
                // connected instead of jumping at the end.
                val barPage = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
                val bm = ((barPage % pageCount) + pageCount) % pageCount
                if (bm in 1..4) {
                    ToolTabs(
                        selected = bm - 1,
                        onSelect = { i ->
                            var d = (1 + i) - bm
                            if (d > pageCount / 2) d -= pageCount
                            if (d < -pageCount / 2) d += pageCount
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + d)
                            }
                        }
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1
                ) { page ->
                    val m = ((page % pageCount) + pageCount) % pageCount
                    when {
                        m == 0 -> HomeScreen(
                            data = data,
                            syncMessage = syncMessage,
                            onSync = { doSync() },
                            onTest = { scope.launch { scheduleTestAlarm(context, store) } },
                            onDeleteMany = { list -> deleteAlarms(list) }
                        )
                        m in 1..4 -> {
                            when (m - 1) {
                                0 -> NormalAlarmsScreen(data, persistThenSync = ::persistThenSync)
                                1 -> WorldClockView(data, persistThenSync = ::persistThenSync)
                                2 -> StopwatchView()
                                else -> TimerView(
                                    data, persistThenSync = ::persistThenSync,
                                    timerSel.first, timerSel.second, timerSel.third,
                                    onSel = { h, min, s -> timerSel = Triple(h, min, s) }
                                )
                            }
                        }
                        m == 5 -> ProfilesScreen(data, persistThenSync = ::persistThenSync)
                        m == 6 -> CalendarScreen(data, persistThenSync = ::persistThenSync)
                        else -> SettingsScreen(data, persistThenSync = ::persistThenSync)
                    }
                }
            }
        }
    }

    if (showTutorial) {
        TutorialScreen(onFinished = {
            showTutorial = false
            persistThenSync { d ->
                d.copy(settings = d.settings.copy(tutorialDone = true))
            }
        })
    }
}

/** Top banner ad. Uses Google's official TEST ad unit unless the user has
 *  saved their own AdMob unit id in Settings. */
@Composable
fun AdBanner(adUnitId: String) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            com.google.android.gms.ads.AdView(ctx).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                this.adUnitId =
                    adUnitId.ifBlank { "ca-app-pub-3940256099942544/6300978111" }
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

suspend fun scheduleTestAlarm(context: Context, store: Store) {
    val data = store.data.first()
    val test = AlarmEntry(
        id = 260_000_000L,
        groupId = 0L,
        triggerAt = System.currentTimeMillis() + 15_000L,
        label = t("Test alarm", "測試鬧鐘"),
        kind = "test"
    )
    val oldTests = data.scheduled.filter { it.kind == "test" }
    oldTests.forEach { AlarmScheduler.cancel(context, it) }
    val kept = data.scheduled.filterNot { it.kind == "test" } + test
    AlarmScheduler.schedule(context, test)
    store.save(data.copy(scheduled = kept))
    runCatching { CountdownNotificationManager.checkAndShowCountdown(context) }
}

fun relative(from: Long, to: Long): String {
    val diff = (to - from) / 60000L
    val h = diff / 60
    val m = diff % 60
    return if (L10n.lang == "zh") {
        if (h > 0) "${h} 小時 ${m} 分鐘" else "${m} 分鐘"
    } else {
        if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    data: AppData,
    syncMessage: String?,
    onSync: () -> Unit,
    onTest: () -> Unit,
    onDeleteMany: (List<AlarmEntry>) -> Unit
) {
    val now = System.currentTimeMillis()
    val upcoming = data.scheduled.filter { it.triggerAt > now }.sortedBy { it.triggerAt }
    val next = upcoming.firstOrNull()
    val dayFmt = remember(L10n.lang) {
        SimpleDateFormat(
            if (L10n.lang == "zh") "M月d日 (E)" else "EEE, MMM d",
            L10n.locale
        )
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val byDay = upcoming.groupBy { dayFmt.format(Date(it.triggerAt)) }

    // Multi-select delete (like other alarm apps): long-press a row to enter
    // selection mode, tap rows to toggle selection, then delete all at once.
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selected = emptySet()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectionMode) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                                onDeleteMany(upcoming.filter { it.id in selected })
                                selectionMode = false
                                selected = emptySet()
                            },
                            enabled = selected.isNotEmpty()
                        ) { Text(t("Delete", "刪除")) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        t("Next alarm", "下一個鬧鐘"),
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.primary
                    )
                    if (next != null) {
                        Text(timeFmt.format(Date(next.triggerAt)), fontSize = 54.sp, fontWeight = FontWeight.Bold)
                        Text(next.label, fontSize = 16.sp)
                        Text(
                            t("Rings in ", "將於 ") + relative(now, next.triggerAt) + t(" from now", " 後響起"),
                            fontSize = 13.sp
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            t(
                                "No alarms scheduled yet.\n\nSet up a work profile or a normal alarm and the next alarm will appear here.",
                                "暫時未有排程鬧鐘。\n\n設定「地點設定檔」或「一般鬧鐘」之後，呢度會顯示下一個鬧鐘。"
                            )
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSync) { Text(t("Sync now", "立即同步")) }
                OutlinedButton(onClick = onTest) { Text(t("Test alarm (15s)", "測試鬧鐘（15秒後）")) }
            }
        }
        // Region-alarm indicator: makes it obvious which region normal
        // alarms currently follow after switching regions in Settings.
        if (data.settings.alarmTimezone.isNotBlank()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        t("Region alarms: ", "地區鬧鐘：") + zoneLabel(data.settings.alarmTimezone) +
                            t(" — normal alarms ring at this region's local time", "（一般鬧鐘跟呢個地區嘅當地時間響）"),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
        if (syncMessage != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        syncMessage,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        byDay.forEach { (day, entries) ->
            item {
                Spacer(Modifier.height(6.dp))
                Text(day, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(entries) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selected = if (e.id in selected) selected - e.id else selected + e.id
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    selected = setOf(e.id)
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked = e.id in selected,
                                onCheckedChange = { on ->
                                    selected = if (on) selected + e.id else selected - e.id
                                }
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(timeFmt.format(Date(e.triggerAt)), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(e.label, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
