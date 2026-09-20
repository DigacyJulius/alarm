package com.shiftalarm.app.core

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private data class PermState(val key: String, val granted: Boolean)

@Composable
fun PermissionGateScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    // ALL permissions with live status — nothing ever vanishes, so the user
    // can see Alarms & reminders and Battery exemption granted at the SAME
    // TIME instead of one hiding the other.
    var permStates by remember { mutableStateOf(listOf<PermState>()) }
    var recheckTrigger by remember { mutableStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        recheckTrigger++
    }

    fun checkPermissions() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

        val states = listOf(
            // 1. 通知權限 (Android 13+)
            PermState(
                "notif",
                Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
            ),
            // 2. 鬧鐘和提醒 — the REAL special-access toggle via AppOps,
            //    completely independent of the battery exemption.
            PermState("exact_alarm", ExactAlarmPermission.isToggleGranted(context)),
            // 3. 勿擾模式繞過
            PermState(
                "dnd",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || nm.isNotificationPolicyAccessGranted
            ),
            // 4. 全螢幕通知 (Android 14+)
            PermState(
                "fullscreen",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm.canUseFullScreenIntent()
            ),
            // 5. 在其他應用上層顯示
            PermState("overlay", Settings.canDrawOverlays(context)),
            // 6. 電池優化豁免
            PermState("battery", pm.isIgnoringBatteryOptimizations(context.packageName))
        )

        permStates = states

        if (states.all { it.granted }) {
            onAllGranted()
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    // Re-check automatically whenever the user returns from a system settings
    // screen, so the gate clears itself without tapping the button.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, recheckTrigger) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exactMissing = permStates.any { it.key == "exact_alarm" && !it.granted }
    val batteryExempt = permStates.any { it.key == "battery" && it.granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            t("Permissions needed", "需要重要權限"),
            fontSize = 22.sp,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Text(
            t(
                "So your alarms ring even when the app is closed or the phone is asleep, please grant these permissions:",
                "為了讓鬧鐘在 App 關閉或手機休眠時仍然正常響起，請授予以下權限："
            ),
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        permStates.forEach { st ->
            val permLabel = when (st.key) {
                "notif" -> t("Notifications", "通知權限")
                "exact_alarm" -> t("Alarms & reminders", "鬧鐘和提醒")
                "dnd" -> t("Do-Not-Disturb override", "勿擾模式繞過")
                "fullscreen" -> t("Full-screen alarm notifications", "全螢幕鬧鐘通知")
                "overlay" -> t("Display over other apps", "在其他應用上層顯示")
                "battery" -> t("Battery optimization exemption", "電池優化豁免")
                else -> st.key
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(permLabel, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (st.granted) {
                    Text(
                        "✓ " + t("Granted", "已開"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        t("Not granted", "未開"),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!st.granted) {
                Button(
                    onClick = {
                        when (st.key) {
                            "notif" -> {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            "exact_alarm" -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }
                            "dnd" -> {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                            "fullscreen" -> {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                            "overlay" -> {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                            "battery" -> {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t("Grant: ", "授予：") + permLabel)
                }
            }

            // Deadlock escape: on many phones the system HIDES the
            // Alarms & reminders toggle while the battery exemption is on.
            // Give the user an explicit way out: temporarily drop the
            // exemption, flip the alarm toggle, then re-grant both.
            if (st.key == "exact_alarm" && !st.granted && batteryExempt) {
                Text(
                    t(
                        "Your phone hides this toggle while battery exemption is ON. Tap the button below to open the battery list, set this app back to Optimized, then come back and grant this — after that you can turn the exemption on again. Both permissions CAN be on at the same time.",
                        "你部機喺電池豁免開啟時會收埋呢個掣。先撳下面按鈕去電池清單將本 app 改返「最佳化」，返嚟開呢個掣，之後再重新開電池豁免——兩個權限可以同時開。"
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (st.key == "battery" && st.granted && exactMissing) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t("Temporarily remove exemption (open battery list)", "暫時移除豁免（開電池清單）"))
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(10.dp))
        Button(onClick = { checkPermissions() }) {
            Text(t("I've granted the permissions — recheck", "我已授予權限，重新檢查"))
        }
        Spacer(Modifier.height(24.dp))
    }
}
