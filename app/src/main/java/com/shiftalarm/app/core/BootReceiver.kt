package com.shiftalarm.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Re-register the stored alarms IMMEDIATELY. A full sync via
        // WorkManager can be deferred by Doze for a long time after a reboot
        // (or after the exact-alarm permission is re-granted), and existing
        // alarms must not wait for it.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = Store(context).data.first()
                val now = System.currentTimeMillis()
                for (entry in data.scheduled.filter { it.triggerAt > now }) {
                    runCatching { AlarmScheduler.schedule(context, entry) }
                }
            } finally {
                pendingResult.finish()
            }
        }

        // Then run a full sync in the background as usual.
        val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "boot_sync", ExistingWorkPolicy.KEEP, req
        )

        // Re-enroll the notification-free alarm watchdog after a reboot.
        runCatching { AlarmWatchdogWorker.ensureScheduled(context) }
    }
}
