package com.example.tasksync.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Trang thai dong bo cua mot cong viec. */
enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED }

/** Mot cong viec: vua la bang trong Room, vua la model dung chung toan app. */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L,          // gio cap nhat cua SERVER
    val version: Int = 0,              // 0 = chua len server lan nao
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val localUpdatedAt: Long = 0L      // gio nguoi dung sua tren may
)
