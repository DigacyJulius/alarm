package com.shiftalarm.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.shiftalarm.app.core.CountdownNotificationManager
import com.shiftalarm.app.data.AlarmEntry
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {

    companion object {
        const val CHANNEL_ID = "alarm_ringing"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "\u9b27\u9418\u97ff\u9435", NotificationManager.IMPORTANCE_HIGH)
            ch.setBypassDnd(true)
            nm.createNotificationChannel(ch)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)
        val entry = runBlocking {
            Store(this@AlarmRingingActivity).data.first().scheduled.firstOrNull { it.id == id }
        }
        // The alarm sound and vibration are owned by AlarmForegroundService,
        // so this activity only provides the UI (dismiss / snooze).

        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val label = entry?.label ?: "\u9b27\u9418"

        setContent {
            MaterialTheme {
                RingingScreen(
                    timeText = timeText,
                    label = label,
                    onSnooze = {
                        runBlocking { snooze(id) }
                        stopAndFinish(id)
                    },
                    onDismiss = {
                        runBlocking { dismiss(id) }
                        stopAndFinish(id)
                    }
                )
            }
        }
    }

    private fun stopAndFinish(id: Long) {
        stopAlarmService()
        runCatching { NotificationManagerCompat.from(this).cancel(id.toInt()) }
        finish()
    }

    private fun stopAlarmService() {
        // stopService() is allowed even when the app is backgrounded (unlike
        // startService()); the service cleans up in onDestroy().
        runCatching {
            stopService(Intent(this, AlarmForegroundService::class.java))
        }
    }

    private suspend fun snooze(id: Long) {
        val store = Store(this)
        val data = store.data.first()
        val entry = data.scheduled.firstOrNull { it.id == id } ?: return
        val nextId = ((data.scheduled
            .filter { it.id >= 250_000_000L && it.id < 260_000_000L }
            .maxOfOrNull { it.id } ?: 249_999_999L) + 1L)
        val snoozeEntry = AlarmEntry(
            id = nextId,
            groupId = entry.groupId,
            triggerAt = System.currentTimeMillis() + data.settings.snoozeMinutes * 60_000L,
            label = entry.label,
            kind = entry.kind,
            isSnooze = true
        )
        val kept = data.scheduled.filterNot { it.id == id } + snoozeEntry
        AlarmScheduler.cancel(this, entry)
        AlarmScheduler.schedule(this, snoozeEntry)
        store.save(data.copy(scheduled = kept))
        
        // Update countdown notification after snooze
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { CountdownNotificationManager.checkAndShowCountdown(this@AlarmRingingActivity) }
        }
    }

    private suspend fun dismiss(id: Long) {
        val store = Store(this)
        val data = store.data.first()
        val entry = data.scheduled.firstOrNull { it.id == id } ?: return
        // Dismiss only the alarm that actually rang. The group's other
        // alarms (e.g. backups later in the day) keep their schedule and
        // can be handled one by one when they fire.
        AlarmScheduler.cancel(this, entry)
        val kept = data.scheduled.filterNot { it.id == entry.id }
        store.save(data.copy(scheduled = kept))
        
        // Update countdown notification after dismissal
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { CountdownNotificationManager.checkAndShowCountdown(this@AlarmRingingActivity) }
        }
    }

    override fun onDestroy() {
        // Only stop the service-owned sound when the activity is really going
        // away (back press / dismiss / snooze). On configuration changes
        // (rotation) the activity is recreated, so the alarm must keep ringing.
        if (isFinishing) {
            stopAlarmService()
        }
        super.onDestroy()
    }
}

@Composable
fun RingingScreen(timeText: String, label: String, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101014)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeText, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color(0xFFCCCCCC), fontSize = 20.sp)
            Spacer(Modifier.height(64.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                OutlinedButton(onClick = onSnooze) {
                    Text(t("Snooze", "貪睡"), color = Color.White, fontSize = 18.sp)
                }
                Button(onClick = onDismiss) {
                    Text(t("Dismiss", "解除"), fontSize = 18.sp)
                }
            }
        }
    }
}
