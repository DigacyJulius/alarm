package com.shiftalarm.app.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.shiftalarm.app.MainActivity
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when a user-set timer finishes: posts a heads-up notification with
 * the default timer sound. The timer itself is an ordinary AlarmManager
 * broadcast, so it keeps running when the app is closed.
 */
class TimerReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "timer_done"
        const val NOTIFICATION_ID = 88888
        const val REQUEST_CODE = 280_000_000L

        /** Schedule (or clear) the system alarm for the stored timer end. */
        fun schedule(context: Context, endAt: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(context)
            if (endAt <= System.currentTimeMillis()) {
                am.cancel(pi)
                return
            }
            runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
            }.onFailure {
                runCatching { am.set(AlarmManager.RTC_WAKEUP, endAt, pi) }
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(context))
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(NOTIFICATION_ID)
            }
        }

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE.toInt(),
            Intent(context, TimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        L10n.syncFromDisk(context)
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.shiftalarm.app.R.drawable.ic_notification)
            .setContentTitle(t("Timer done", "計時結束"))
            .setContentText(t("Ding! Time's up.", "叮！時間到喇。"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
        // Clear the stored timer so the UI stops counting down.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = Store(context)
                val data = store.data.first()
                if (data.timerEndAt in 1..System.currentTimeMillis() + 1000L) {
                    store.save(data.copy(timerEndAt = 0))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, t("Timer done", "計時結束"), NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = t("Heads-up when the timer finishes", "計時器時間到嘅提示")
        channel.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        nm.createNotificationChannel(channel)
    }
}
