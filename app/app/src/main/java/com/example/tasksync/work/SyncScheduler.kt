package com.example.tasksync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.tasksync.sync.SyncUiState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged


class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun requestSync() = enqueueOneTime(ExistingWorkPolicy.KEEP)

    fun requestSyncNow() = enqueueOneTime(ExistingWorkPolicy.REPLACE)

    private fun enqueueOneTime(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, policy, request)
    }

    fun observeSyncState(): Flow<SyncUiState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME)
    ) { oneTime, periodic ->
        val one = oneTime.firstOrNull()?.state
        when {
            one == WorkInfo.State.RUNNING || periodic.any { it.state == WorkInfo.State.RUNNING } ->
                SyncUiState.RUNNING
            // ENQUEUED = đang chờ có mạng hoặc đang chờ backoff để thử lại
            one == WorkInfo.State.ENQUEUED || one == WorkInfo.State.BLOCKED -> SyncUiState.WAITING
            one == WorkInfo.State.SUCCEEDED -> SyncUiState.SUCCEEDED
            one == WorkInfo.State.FAILED -> SyncUiState.FAILED
            else -> SyncUiState.IDLE
        }
    }.distinctUntilChanged()

    private companion object {
        const val ONE_TIME_WORK_NAME = "tasksync_sync_now"
        const val PERIODIC_WORK_NAME = "tasksync_sync_periodic"
        const val BACKOFF_SECONDS = 10L
    }
}
