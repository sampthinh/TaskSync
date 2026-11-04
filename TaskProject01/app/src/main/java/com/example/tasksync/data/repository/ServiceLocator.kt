package com.example.tasksync.data.repository

import android.content.Context
import com.example.tasksync.data.local.AppDatabase

/** Noi lay Repository dung chung. Dung: ServiceLocator.taskRepository(applicationContext) */
object ServiceLocator {
    @Volatile
    private var repository: TaskRepository? = null

    fun taskRepository(context: Context): TaskRepository =
        repository ?: synchronized(this) {
            repository ?: TaskRepositoryImpl(
                AppDatabase.get(context).taskDao(),
                context.applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            ).also { repository = it }
        }
}
