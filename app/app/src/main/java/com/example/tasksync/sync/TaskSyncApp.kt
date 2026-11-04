package com.example.tasksync.sync

import android.app.Application
import com.example.tasksync.work.SyncScheduler

class TaskSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val scheduler = SyncScheduler(this)
        scheduler.schedulePeriodic()
        scheduler.requestSync()
    }
}