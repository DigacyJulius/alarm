package com.shiftalarm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shiftalarm.app.data.AlarmEntry

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    private fun pending(context: Context, entry: AlarmEntry): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ID, entry.id)
        }
        return PendingIntent.getBroadcast(
            context,
            entry.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingActivity(context: Context, entry: AlarmEntry): PendingIntent {
        val intent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, entry.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            entry.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1) setAlarmClock(): the system treats this as a user-facing alarm
        //    clock, never defers it, shows it on the lock screen, and leaves
        //    low-power modes to deliver it. The right primary API for an
        //    alarm app whose whole job is to ring on time.
        try {
            val info = AlarmManager.AlarmClockInfo(entry.triggerAt, pendingActivity(context, entry))
            am.setAlarmClock(info, pending(context, entry))
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "setAlarmClock rejected, trying setExactAndAllowWhileIdle", e)
        }

        // 2) setExactAndAllowWhileIdle(): exact + Doze-capable (older Android
        //    and OEM quirks).
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry)
            )
            return
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing - alarm ${entry.id} will only ring inexactly", e)
        }

        // 3) setAndAllowWhileIdle(): the ORIGINAL commit-58 fallback — no
        //    permission needed, not exact but still wakes the device in
        //    Doze. An alarm is NEVER silently dropped.
        runCatching {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, entry.triggerAt, pending(context, entry)
            )
        }.onFailure {
            Log.e(TAG, "Failed to schedule alarm ${entry.id}", it)
        }

        if (!ExactAlarmPermission.isGranted(context)) {
            PermissionNudgeEvent.broadcast(context)
        }
    }

    fun cancel(context: Context, entry: AlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, entry))
    }
}
