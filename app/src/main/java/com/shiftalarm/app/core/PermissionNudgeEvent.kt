package com.shiftalarm.app.core

import android.content.Context
import android.content.Intent

object PermissionNudgeEvent {
    const val ACTION = "com.shiftalarm.app.PERMISSION_NUDGE"

    fun broadcast(context: Context) {
        val intent = Intent(ACTION).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}