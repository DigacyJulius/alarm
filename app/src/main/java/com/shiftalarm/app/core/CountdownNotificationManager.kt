package com.shiftalarm.app.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shiftalarm.app.MainActivity
import com.shiftalarm.app.R
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Singleton: shows a silent countdown notification ONLY when the next alarm
 * is within 3 minutes of firing (user preference — no other persistent
 * notification exists). The update loop re-reads the stored schedule every
 * tick, so a deleted alarm's notification disappears immediately instead of
 * counting down to a fire time that no longer exists.
 */
object CountdownNotificationManager {

    const val CHANNEL_ID = "upcoming_alarm"
    const val NOTIFICATION_ID = 99999
    private const val COUNTDOWN_THRESHOLD_MINUTES = 3L
    private const val PRE_ALARM_LEAD_MS = 3 * 60_000L
    private const val PRE_ALARM_REQUEST_CODE = 270_000_000

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null
    private var appContext: Context? = null

    private fun contextOf(context: Context): Context {
        if (appContext == null) appContext = context.applicationContext
        return appContext!!
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                t("Upcoming alarm", "即將鬧鐘"),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = t("Heads-up before an alarm rings", "提示即將響起的鬧鐘")
            nm.createNotificationChannel(channel)
        }
    }

    suspend fun checkAndShowCountdown(context: Context) {
        val c = contextOf(context)
        ensureChannel(c)

        // Cancel any existing update job
        updateJob?.cancel()

        val data = Store(c).data.first()
        if (data.settings.language.isNotEmpty()) L10n.lang = data.settings.language
        val now = System.currentTimeMillis()
        val threshold = now + TimeUnit.MINUTES.toMillis(COUNTDOWN_THRESHOLD_MINUTES)

        // Find the next upcoming alarm within 10 minutes
        val nextAlarm = data.scheduled
            .filter { it.triggerAt > now && it.triggerAt <= threshold }
            .minByOrNull { it.triggerAt }

        if (nextAlarm != null) {
            showOrUpdateNotification(c, nextAlarm, now)
            startCountdownUpdates(c, nextAlarm)
        } else {
            cancelNotification()
        }
    }

    /**
     * Schedule a silent broadcast ~3 minutes before the next alarm so the
     * countdown notification appears on time even when the app process is
     * not running. Called after every sync, watchdog run and boot; chained
     * by [PreAlarmReceiver] itself.
     */
    suspend fun ensurePreAlarm(context: Context) {
        val c = contextOf(context)
        val data = Store(c).data.first()
        val now = System.currentTimeMillis()
        val upcoming = data.scheduled.filter { it.triggerAt > now }.sortedBy { it.triggerAt }

        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, PRE_ALARM_REQUEST_CODE,
            Intent(c, PreAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // First alarm whose pre-alarm time is still in the future. If the
        // next alarm is already inside the 3-minute window, show the
        // notification now and let the receiver/watchdog chain the rest.
        val target = upcoming.firstOrNull { it.triggerAt - PRE_ALARM_LEAD_MS > now }
        if (target == null) {
            if (upcoming.isEmpty()) am.cancel(pi) else checkAndShowCountdown(c)
            return
        }

        val at = target.triggerAt - PRE_ALARM_LEAD_MS
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.onFailure {
            runCatching { am.set(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    private fun showOrUpdateNotification(context: Context, alarm: AlarmEntry, now: Long) {
        val remainingMillis = alarm.triggerAt - now
        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
        val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60

        val timeText = String.format(Locale.getDefault(), "%02d:%02d", remainingMinutes, remainingSeconds)
        val alarmTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.triggerAt))

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t("Alarm ringing soon", "鬧鐘即將響起"))
            .setContentText("${alarm.label} • $timeText" + t(" (left)", " (剩餘)"))
            .setSubText(t("Heads-up for the ", "為 ") + alarmTime + t(" alarm", " 嘅鬧鐘備計"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun startCountdownUpdates(context: Context, alarm: AlarmEntry) {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remainingMillis = alarm.triggerAt - now

                if (remainingMillis <= 0) {
                    // Alarm should have fired, cancel notification
                    cancelNotification()
                    break
                }

                // The alarm may have been deleted or changed while counting
                // down — re-check the stored schedule every tick so the
                // notification reflects reality.
                val stillScheduled = Store(context).data.first()
                    .scheduled.any { it.id == alarm.id }
                if (!stillScheduled) {
                    cancelNotification()
                    break
                }

                handler.post {
                    showOrUpdateNotification(context, alarm, now)
                }
                delay(1000)
            }
        }
    }

    private fun cancelNotification() {
        updateJob?.cancel()
        updateJob = null
        val c = appContext ?: return
        runCatching { NotificationManagerCompat.from(c).cancel(NOTIFICATION_ID) }
    }
}
