package com.example.tasksync.data.repository

import android.content.SharedPreferences
import com.example.tasksync.data.local.TaskDao
import com.example.tasksync.data.model.SyncStatus
import com.example.tasksync.data.model.Task
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TaskRepositoryImpl(
    private val dao: TaskDao,
    private val prefs: SharedPreferences
) : TaskRepository {

    private fun now() = System.currentTimeMillis()

    // ---------- Cho giao dien: chi dung Room, khong can mang ----------

    override fun observeTasks(): Flow<List<Task>> = dao.observeTasks()

    override suspend fun addTask(title: String, description: String) {
        dao.upsert(
            Task(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                version = 0,
                syncStatus = SyncStatus.PENDING,
                localUpdatedAt = now()
            )
        )
    }

    override suspend fun updateTask(id: String, title: String, description: String) {
        val t = dao.getById(id) ?: return
        dao.upsert(
            t.copy(
                title = title,
                description = description,
                syncStatus = SyncStatus.PENDING,
                localUpdatedAt = now()
            )
        )
    }

    override suspend fun setCompleted(id: String, completed: Boolean) {
        val t = dao.getById(id) ?: return
        dao.upsert(t.copy(isCompleted = completed, syncStatus = SyncStatus.PENDING, localUpdatedAt = now()))
    }

    override suspend fun deleteTask(id: String) {
        val t = dao.getById(id) ?: return
        // Xoa logic: chi danh dau, de con bao cho server biet
        dao.upsert(t.copy(isDeleted = true, syncStatus = SyncStatus.PENDING, localUpdatedAt = now()))
    }

    // ---------- Cho dong bo ----------

    override suspend fun getPendingTasks(): List<Task> = dao.getPending()

    override suspend fun markSyncing(ids: List<String>) {
        if (ids.isNotEmpty()) dao.setStatus(ids, SyncStatus.SYNCING)
    }

    override suspend fun markSynced(serverTask: Task) {
        val current = dao.getById(serverTask.id)
        if (current != null && current.syncStatus == SyncStatus.PENDING) {
            // Nguoi dung vua sua tiep trong luc dang dong bo: giu noi dung moi,
            // chi cap nhat version/updatedAt cua server de lan sau gui dung baseVersion.
            dao.upsert(current.copy(version = serverTask.version, updatedAt = serverTask.updatedAt))
        } else {
            dao.upsert(
                serverTask.copy(
                    syncStatus = SyncStatus.SYNCED,
                    localUpdatedAt = serverTask.updatedAt
                )
            )
        }
    }

    override suspend fun markFailed(ids: List<String>) {
        if (ids.isNotEmpty()) dao.failSyncing(ids)
    }

    override suspend fun applyServerChanges(changes: List<Task>) {
        val toSave = mutableListOf<Task>()
        for (server in changes) {
            val local = dao.getById(server.id)
            // Viec dang cho gui / dang gui / that bai o may: bo qua, se duoc day len sau
            val busy = local != null && local.syncStatus != SyncStatus.SYNCED
            if (!busy) {
                toSave.add(
                    server.copy(
                        syncStatus = SyncStatus.SYNCED,
                        localUpdatedAt = server.updatedAt
                    )
                )
            }
        }
        if (toSave.isNotEmpty()) dao.upsertAll(toSave)
    }

    override suspend fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    override suspend fun setLastSyncTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, time).apply()
    }

    // ---------- Xung dot: Last-Write-Wins ----------

    override fun resolveConflict(local: Task, server: Task): ConflictResult =
        if (local.localUpdatedAt > server.updatedAt) {
            ConflictResult.KeepLocal
        } else {
            ConflictResult.TakeServer(server)
        }

    private companion object {
        const val KEY_LAST_SYNC = "last_sync_time"
    }
}
