package com.shiftalarm.app.core

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmForegroundService : Service() {

    companion object {
        // Safety net: stop ringing after 10 minutes even if the user never
        // interacts (matches typical system alarm behaviour).
        private const val MAX_RINGING_MS = 10 * 60_000L
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var alarmId: Long = -1L
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        L10n.syncFromDisk(this)
        alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()

        val launch = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullPi = PendingIntent.getActivity(
            this, alarmId.toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AlarmRingingActivity.CHANNEL_ID)
            .setSmallIcon(com.shiftalarm.app.R.drawable.ic_notification)
            .setContentTitle(t("Alarm", "鬧鐘響起"))
            .setContentText(t("Tap to view", "點按查看"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullPi, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        // Post the notification (and the full-screen intent) before preparing
        // the media player, so the alert UI is not delayed by sound setup.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, alarmId.toInt(), notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(alarmId.toInt(), notif)
            }
        }

        startRinging()

        // The service owns the alarm sound. The full-screen intent launches the
        // ringing activity when allowed; either way the alarm is audible even
        // if the activity is blocked (background activity launch restrictions).

        handler.postDelayed({
            cleanupAndStop()
        }, MAX_RINGING_MS)

        return START_STICKY
    }

    /**
     * Play the default alarm sound (looping, USAGE_ALARM) and vibrate until the
     * ringing activity dismisses/snoozes or the safety timeout fires.
     */
    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setDataSource(this@AlarmForegroundService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }
        runCatching {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        }
    }

    private fun stopRinging() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "ShiftAlarm:AlarmWakeLock"
            ).apply { acquire(MAX_RINGING_MS) }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun cleanupAndStop() {
        handler.removeCallbacksAndMessages(null)
        stopRinging()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRinging()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
