package com.example.tasksync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.tasksync.sync.SyncManager
import com.example.tasksync.sync.SyncOutcome

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (SyncManager.get(applicationContext).sync()) {
            SyncOutcome.SUCCESS -> Result.success()
            SyncOutcome.FAILED -> Result.failure()
            SyncOutcome.RETRY ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
