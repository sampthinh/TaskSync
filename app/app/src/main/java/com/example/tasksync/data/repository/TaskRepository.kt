package com.example.tasksync.data.repository

import com.example.tasksync.data.model.Task
import kotlinx.coroutines.flow.Flow

/** Ket qua khi xu ly xung dong (Last-Write-Wins). */
sealed class ConflictResult {
    /** Ban tren may moi hon: gui lai PUT voi force = true. */
    object KeepLocal : ConflictResult()

    /** Ban server moi hon: bo ban tren may, dung markSynced(server). */
    data class TakeServer(val server: Task) : ConflictResult()
}

interface TaskRepository {
    // --- Cho giao dien ---
    fun observeTasks(): Flow<List<Task>>
    suspend fun addTask(title: String, description: String)
    suspend fun updateTask(id: String, title: String, description: String)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun deleteTask(id: String)

    // --- Cho dong bo ---
    suspend fun getPendingTasks(): List<Task>
    suspend fun markSyncing(ids: List<String>)
    suspend fun markSynced(serverTask: Task)
    suspend fun markFailed(ids: List<String>)
    suspend fun applyServerChanges(changes: List<Task>)
    suspend fun getLastSyncTime(): Long
    suspend fun setLastSyncTime(time: Long)
    fun resolveConflict(local: Task, server: Task): ConflictResult
}
