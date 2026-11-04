package com.example.tasksync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasksync.data.model.Task
import com.example.tasksync.data.repository.TaskRepository
import com.example.tasksync.work.SyncScheduler
import com.example.tasksync.sync.SyncUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    private val repository: TaskRepository,
    private val scheduler: SyncScheduler
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncState: StateFlow<SyncUiState> = scheduler.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState.IDLE)

    fun addTask(title: String, description: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        changeAndSync { repository.addTask(cleanTitle, description.trim()) }
    }

    fun updateTask(id: String, title: String, description: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        changeAndSync { repository.updateTask(id, cleanTitle, description.trim()) }
    }

    fun setCompleted(id: String, completed: Boolean) {
        changeAndSync { repository.setCompleted(id, completed) }
    }

    fun deleteTask(id: String) {
        changeAndSync { repository.deleteTask(id) }
    }

    fun syncNow() = scheduler.requestSyncNow()

    private fun changeAndSync(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()                    // ghi vào Room trước, giao diện cập nhật ngay
            scheduler.requestSync()     // rồi mới xin đồng bộ
        }
    }
}
