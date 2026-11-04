package com.example.tasksync.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tasksync.data.repository.ServiceLocator
import com.example.tasksync.work.SyncScheduler

class TaskViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return TaskViewModel(ServiceLocator.taskRepository(app), SyncScheduler(app)) as T
    }
}
