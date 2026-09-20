package com.shiftalarm.app.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            SyncEngine.sync(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
