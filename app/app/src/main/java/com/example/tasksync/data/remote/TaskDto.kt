package com.example.tasksync.data.remote

import com.example.tasksync.data.model.SyncStatus
import com.example.tasksync.data.model.Task
import com.google.gson.Gson
import retrofit2.Response

/** Dang JSON cua mot cong viec khi gui/nhan voi server. */
data class TaskDto(
    val id: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L,
    val version: Int = 0
)

/** Body cua yeu cau sua (PUT). */
data class UpdateDto(
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val isDeleted: Boolean,
    val baseVersion: Int,
    val force: Boolean = false
)

/** Ket qua GET /tasks/changes. */
data class ChangesDto(
    val serverTime: Long = 0L,
    val changes: List<TaskDto> = emptyList()
)

/** Body server tra ve khi xung dong (409). */
data class ConflictDto(
    val error: String? = null,
    val server: TaskDto? = null
)

fun TaskDto.toTask(status: SyncStatus = SyncStatus.SYNCED) = Task(
    id = id, title = title, description = description,
    isCompleted = isCompleted, isDeleted = isDeleted,
    updatedAt = updatedAt, version = version,
    syncStatus = status, localUpdatedAt = updatedAt
)

fun Task.toDto() = TaskDto(
    id = id, title = title, description = description,
    isCompleted = isCompleted, isDeleted = isDeleted,
    updatedAt = updatedAt, version = version
)

/** Lay ban cua server tu phan hoi 409. Tra null neu doc khong duoc. */
fun Response<*>.conflictServerTask(): TaskDto? = try {
    Gson().fromJson(errorBody()?.string(), ConflictDto::class.java)?.server
} catch (e: Exception) {
    null
}
