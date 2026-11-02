package com.example.tasksync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tasksync.data.model.SyncStatus
import com.example.tasksync.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** Giao dien lang nghe ham nay: du lieu doi la danh sach tu cap nhat. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY localUpdatedAt DESC")
    fun observeTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): Task?

    /** Viec can gui len server (ke ca viec da xoa logic). */
    @Query("SELECT * FROM tasks WHERE syncStatus IN ('PENDING', 'FAILED')")
    suspend fun getPending(): List<Task>

    /** Them moi hoac ghi de neu da co cung id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: Task)

    /** Them nhieu viec trong MOT transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<Task>)

    @Query("UPDATE tasks SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun setStatus(ids: List<String>, status: SyncStatus)

    /** Chi doi sang FAILED neu viec van dang SYNCING (tranh de len viec nguoi dung vua sua). */
    @Query("UPDATE tasks SET syncStatus = 'FAILED' WHERE id IN (:ids) AND syncStatus = 'SYNCING'")
    suspend fun failSyncing(ids: List<String>)
}
