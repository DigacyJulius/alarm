package com.shiftalarm.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.shiftalarm.app.FIRE_ALARM"
        const val EXTRA_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id == -1L) return

        AlarmRingingActivity.ensureChannel(context)

        // Path A: start the foreground service first so the alarm sound and the
        // notification (with full-screen intent for the screen-off/locked case)
        // kick in as fast as possible.
        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            putExtra(EXTRA_ID, id)
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }

        // Path B: launch the ringing screen directly. With the "display over
        // other apps" permission granted this pops up immediately even when the
        // app is closed; without it Android may silently block the launch and
        // the full-screen intent notification above takes over instead.
        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ID, id)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }
        runCatching { context.startActivity(activityIntent) }
    }
}
