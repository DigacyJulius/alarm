package com.shiftalarm.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires ~3 minutes before the next alarm: shows the countdown notification
 * (even when the app process was not running) and chains the pre-alarm
 * broadcast for the following alarm.
 */
class PreAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { CountdownNotificationManager.checkAndShowCountdown(context) }
                runCatching { CountdownNotificationManager.ensurePreAlarm(context) }
            } finally {
                pending.finish()
            }
        }
    }
}
