package com.shiftalarm.app.core

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

object ExactAlarmPermission {

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * The TRUE state of the "Alarms & reminders" special-access toggle,
     * checked via AppOps and COMPLETELY INDEPENDENT of the battery-exemption
     * allowlist. AlarmManager.canScheduleExactAlarms() conflates the two
     * (it also returns true whenever the app is battery-exempt), which made
     * the permission gate treat the two as either-one: granting battery
     * exemption made the Alarms & reminders item vanish. This check keeps
     * both items visible and grantable at the same time.
     */
    fun isToggleGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        // USE_EXACT_ALARM (Android 13+) is granted at install and cannot be
        // revoked, so when the app holds it exact alarms are always available
        // and the "Alarms & reminders" toggle does not even exist for it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.USE_EXACT_ALARM) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return true
        // "android:schedule_exact_alarm" is the hidden AppOpsManager
        // OPSTR_SCHEDULE_EXACT_ALARM constant (not exposed in the public
        // SDK); it is the exact op behind the Settings toggle.
        val mode = appOps.unsafeCheckOpNoThrow(
            "android:schedule_exact_alarm",
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            // Below Android 12 exact alarms need no special permission.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @Composable
    fun PermissionNudgeDialog(
        onDismiss: () -> Unit,
        onOpenSettings: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("需要精確鬧鐘權限") },
            text = {
                Text(
                    "Android 14 起，App 必須獲得「設定鬧鐘與提醒」權限，\n" +
                    "鬧鐘才能在息屏/省電模式下準時響鈴。\n\n" +
                    "點擊下方按鈕前往系統設定開啟。"
                )
            },
            confirmButton = {
                TextButton(onClick = { onOpenSettings(); onDismiss() }) {
                    Text("前往設定", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("稍後") }
            }
        )
    }
}