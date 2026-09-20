package com.shiftalarm.app.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Watchdog that runs every ~15 minutes WITHOUT any notification (it replaced
 * the old 24/7 standby foreground service, whose persistent notification the
 * user found annoying).
 *
 * Some OEM battery managers discard a background app's registered alarms even
 * when autostart is enabled. This worker re-registers every stored alarm, so
 * even if the system wipes them they come back automatically, and it keeps
 * the 3-minute pre-alarm broadcast scheduled.
 */
class AlarmWatchdogWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext
        runCatching {
            val data = Store(c).data.first()
            val now = System.currentTimeMillis()
            for (old in data.scheduled) AlarmScheduler.cancel(c, old)
            for (e in data.scheduled.filter { it.triggerAt > now }) {
                AlarmScheduler.schedule(c, e)
            }
        }
        runCatching { CountdownNotificationManager.ensurePreAlarm(c) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "alarm_watchdog"
        private const val ONESHOT_NAME = "alarm_watchdog_now"

        /** Enqueue the recurring 15-minute watchdog (idempotent). */
        fun ensureScheduled(context: Context) {
            val req = PeriodicWorkRequestBuilder<AlarmWatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        /** Run the watchdog once right now (e.g. when the app is opened). */
        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<AlarmWatchdogWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_NAME, ExistingWorkPolicy.REPLACE, req
            )
        }
    }
}
