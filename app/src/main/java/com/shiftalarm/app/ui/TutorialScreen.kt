package com.shiftalarm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftalarm.app.core.t

private data class TutorialPage(
    val icon: ImageVector,
    val titleEn: String, val titleZh: String,
    val bodyEn: List<String>, val bodyZh: List<String>
)

private val TUTORIAL_PAGES = listOf(
    TutorialPage(
        Icons.Filled.Alarm, "Welcome to ShiftAlarm", "歡迎使用 ShiftAlarm",
        listOf(
            "ShiftAlarm turns your work roster calendar into alarms automatically. This quick tutorial shows you how to set it up in 4 steps.",
            "You can reopen this tutorial anytime from Settings."
        ),
        listOf(
            "ShiftAlarm 會自動將你嘅更期日曆變成鬧鐘。呢個簡短教學會示範 4 步設定。",
            "你隨時可以喺設定入面重新睇呢個教學。"
        )
    ),
    TutorialPage(
        Icons.Filled.DateRange, "Step 1 — Connect your roster", "第一步 — 連接更期日曆",
        listOf(
            "Four ways to feed your roster: ① paste a Google Calendar private iCal address (Settings → Import & export → Secret address), ② pick device calendars in Settings, ③ import an .ics file in Diagnostics, or ④ tap days on the Calendar page and pick your shift by hand — no calendar app needed.",
            "The app refreshes every hour automatically. Priority: imported .ics → device calendars → iCal URL. Calendar-page entries always count."
        ),
        listOf(
            "更期來源有四種：① 喺設定貼上 Google 日曆嘅私人 iCal 網址（設定 → 匯入和匯出 → iCal 私人網址）；② 喺設定揀選裝置日曆；③ 喺「診斷」匯入 .ics 檔案；④ 直接喺「日曆」分頁撳日期手動揀更份，唔使任何日曆 app。",
            "App 每小時自動更新一次。優先次序：匯入 .ics → 裝置日曆 → iCal 網址；「日曆」分頁手動填嘅更一定會計。"
        )
    ),
    TutorialPage(
        Icons.Filled.Place, "Step 2 — Profiles & shift types", "第二步 — 地點設定檔同更種",
        listOf(
            "In the Profiles tab, create a profile per work location (e.g. CMC) with its keyword, and shift types with their code letters and wake-up times (e.g. code \"a\" = early shift, wake 07:00).",
            "ShiftAlarm matches your calendar events against these keywords and schedules the right alarm for every shift."
        ),
        listOf(
            "喺「設定檔」分頁，每個工作地點開一個設定檔（例：CMC）並填關鍵字；再設定更種代號同起身時間（例：代號 a＝早更，07:00 起身）。",
            "ShiftAlarm 會用呢啲關鍵字比對日曆事件，為每個更自動排啱時間嘅鬧鐘。"
        )
    ),
    TutorialPage(
        Icons.Filled.Delete, "Deleting & restoring alarms", "刪除同還原鬧鐘",
        listOf(
            "On the Home tab, long-press any alarm to reveal its delete button — tapping it removes just that alarm; other alarms on the same day stay untouched.",
            "Deleted alarms are listed in Settings → Deleted alarms, where you can restore them individually. Expired deleted alarms disappear automatically."
        ),
        listOf(
            "喺「首頁」長撳任何一粒鬧鐘會浮現刪除掣；撳佢只會刪嗰一粒鬧鐘，同日其他鬧鐘唔受影響。",
            "已刪嘅鬧鐘會列喺「設定 → 已刪除鬧鐘管理」，可以逐粒還原。過咗時間嘅已刪鬧鐘會自動消失。"
        )
    ),
    TutorialPage(
        Icons.Filled.Language, "Travel & normal alarms", "旅行同一般鬧鐘",
        listOf(
            "In the Tools tab → Alarms you'll find your own alarms, independent of the roster — one-off or weekly repeating. The Tools tab also has a world clock, stopwatch and timer.",
            "In Settings → Alarms → Region alarm you can pin alarms to a timezone, so 07:00 keeps ringing at home-region time while you travel."
        ),
        listOf(
            "「工具 → 鬧鐘」係你自己嘅一般鬧鐘，獨立於更期 — 可設一次性或每週重複；「工具」分頁仲有世界時鐘、碼錶同計時器。",
            "「設定 → 鬧鐘 → 地區鬧鐘」可以將鬧鐘綁定時區，去旅行時 07:00 照樣喺家鄉時間響。"
        )
    ),
    TutorialPage(
        Icons.Filled.BatteryStd, "Keep alarms reliable", "保持鬧鐘可靠",
        listOf(
            "For dependable alarms: in Settings, tap \"Exempt battery optimization\" (strongly recommended) and allow exact alarms when offered.",
            "Avoid force-stopping the app — Android blocks everything for a force-stopped app until you open it again. A background watchdog re-registers alarms every 15 minutes, so swiping the app away is fine."
        ),
        listOf(
            "想鬧鐘可靠：喺設定撳「豁免電池優化」（強烈建議），並喺有提示時允許精確鬧鐘。",
            "唔好「強制停止」app — Android 會封鎖被強制停止嘅 app 一切活動，直到你重新開啟。背景 watchdog 每 15 分鐘會重新登記所有鬧鐘，所以平時掃走 app 係冇問題嘅。"
        )
    ),
    TutorialPage(
        Icons.Filled.Info, "You're all set", "大功告成",
        listOf(
            "Switch languages anytime in Settings → Language. Swipe left/right to move between pages of the app.",
            "That's it — happy sleeping, ShiftAlarm will wake you."
        ),
        listOf(
            "隨時可以喺「設定 → 語言」切換語言。左右滑動就可以切換 app 嘅唔同頁面。",
            "就咁多 — 安心瞓啦，ShiftAlarm 會叫醒你。"
        )
    )
)

/** Full-screen tutorial. Shown once on first launch; reopenable from Settings. */
@Composable
fun TutorialScreen(onFinished: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val p = TUTORIAL_PAGES[page]
    val title = t(p.titleEn, p.titleZh)
    val body = if (com.shiftalarm.app.core.L10n.lang == "zh") p.bodyZh else p.bodyEn
    val last = page == TUTORIAL_PAGES.size - 1

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                p.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp).fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            body.forEach { line ->
                Text(line, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(24.dp))
            // Page dots
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TUTORIAL_PAGES.indices.forEach { i ->
                    Surface(
                        shape = CircleShape,
                        color = if (i == page) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .fillMaxWidth(0.02f)
                    ) {}
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                        Text(t("Back", "上一步"))
                    }
                }
                Button(
                    onClick = { if (last) onFinished() else page++ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (last) t("Get started", "開始使用") else t("Next", "下一步"))
                }
            }
            OutlinedButton(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(t("Skip tutorial", "跳過教學")) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
