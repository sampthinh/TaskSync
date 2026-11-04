package com.example.tasksync.sync

import android.content.Context
import com.example.tasksync.data.model.SyncStatus
import com.example.tasksync.data.model.Task
import com.example.tasksync.data.remote.ApiClient
import com.example.tasksync.data.remote.TaskApi
import com.example.tasksync.data.remote.TaskDto
import com.example.tasksync.data.remote.UpdateDto
import com.example.tasksync.data.remote.conflictServerTask
import com.example.tasksync.data.remote.toDto
import com.example.tasksync.data.remote.toTask
import com.example.tasksync.data.repository.ConflictResult
import com.example.tasksync.data.repository.ServiceLocator
import com.example.tasksync.data.repository.TaskRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

class SyncManager(
    private val repo: TaskRepository,
    private val api: TaskApi = ApiClient.api
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome = mutex.withLock {

        repo.resetStuckSyncing()

        var overall = SyncOutcome.SUCCESS
        for (round in 1..MAX_ROUNDS) {
            val push = pushLocalChanges()
            if (push == SyncOutcome.RETRY) return@withLock SyncOutcome.RETRY

            val pull = pullServerChanges()
            if (pull == SyncOutcome.RETRY) return@withLock SyncOutcome.RETRY

            overall = maxOf(overall, push, pull)

            // Người dùng vừa sửa tiếp trong lúc đang đồng bộ (việc đó đang PENDING): chạy thêm vòng nữa
            val editedDuringSync = repo.getPendingTasks().any { it.syncStatus == SyncStatus.PENDING }
            if (!editedDuringSync) break
        }
        overall
    }

    private suspend fun pushLocalChanges(): SyncOutcome {
        val pending = repo.getPendingTasks()
        if (pending.isEmpty()) return SyncOutcome.SUCCESS

        repo.markSyncing(pending.map { it.id })

        var outcome = SyncOutcome.SUCCESS
        for ((index, task) in pending.withIndex()) {
            val result = try {
                pushOne(task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Mất mạng / timeout: dừng, trả việc hiện tại và các việc chưa gửi về FAILED để thử lại sau
                repo.markFailed(pending.drop(index).map { it.id })
                return SyncOutcome.RETRY
            } catch (e: Exception) {
                // Lỗi không phải mạng (ví dụ JSON sai): không thử lại ngay
                repo.markFailed(listOf(task.id))
                SyncOutcome.FAILED
            }
            outcome = maxOf(outcome, result)
        }
        return outcome
    }

    private suspend fun pushOne(task: Task): SyncOutcome {
        val response = when {
            task.version == 0 -> api.create(task.toDto())                       // chưa lên server lần nào
            task.isDeleted -> api.delete(task.id)                               // đã có trên server, xóa logic
            else -> api.update(task.id, task.toUpdateDto(task.version, force = false))
        }
        return handleResponse(task, response, afterForce = false)
    }

    private suspend fun handleResponse(
        task: Task,
        response: Response<TaskDto>,
        afterForce: Boolean
    ): SyncOutcome {
        val code = response.code()
        return when {
            code == 200 || code == 201 -> confirm(task, response)
            code == 409 && !afterForce -> handleConflict(task, response)
            code in 500..599 -> {
                repo.markFailed(listOf(task.id))
                SyncOutcome.RETRY
            }
            else -> {
                // 4xx khác (kể cả 404): không thử lại
                repo.markFailed(listOf(task.id))
                SyncOutcome.FAILED
            }
        }
    }

    private suspend fun confirm(task: Task, response: Response<TaskDto>): SyncOutcome {
        val body = response.body()
        return if (body != null) {
            repo.markSynced(body.toTask())
            SyncOutcome.SUCCESS
        } else {
            repo.markFailed(listOf(task.id))
            SyncOutcome.RETRY
        }
    }

    private suspend fun handleConflict(local: Task, response: Response<TaskDto>): SyncOutcome {
        val server = response.conflictServerTask()?.toTask()
        if (server == null) {
            repo.markFailed(listOf(local.id))
            return SyncOutcome.FAILED
        }
        return when (val decision = repo.resolveConflict(local, server)) {
            is ConflictResult.TakeServer -> {
                repo.markSynced(decision.server)
                SyncOutcome.SUCCESS
            }
            ConflictResult.KeepLocal -> {
                // Bản cục bộ mới hơn: gửi lại PUT với force = true
                val forced = api.update(local.id, local.toUpdateDto(server.version, force = true))
                handleResponse(local, forced, afterForce = true)
            }
        }
    }

    private suspend fun pullServerChanges(): SyncOutcome {
        val since = repo.getLastSyncTime()
        val response = try {
            api.changes(since)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return SyncOutcome.RETRY
        } catch (e: Exception) {
            return SyncOutcome.FAILED
        }

        return when {
            response.code() == 200 -> {
                val body = response.body() ?: return SyncOutcome.RETRY
                // Repository tự bỏ qua việc đang PENDING / SYNCING / FAILED ở máy
                repo.applyServerChanges(body.changes.map { it.toTask() })
                // Mốc thời gian lấy từ server, không dùng giờ máy
                repo.setLastSyncTime(body.serverTime)
                SyncOutcome.SUCCESS
            }
            response.code() in 500..599 -> SyncOutcome.RETRY
            else -> SyncOutcome.FAILED
        }
    }

    private fun Task.toUpdateDto(baseVersion: Int, force: Boolean) = UpdateDto(
        title = title,
        description = description,
        isCompleted = isCompleted,
        isDeleted = isDeleted,
        baseVersion = baseVersion,
        force = force
    )

    companion object {
        private const val MAX_ROUNDS = 3

        @Volatile
        private var instance: SyncManager? = null

        fun get(context: Context): SyncManager =
            instance ?: synchronized(this) {
                instance ?: SyncManager(ServiceLocator.taskRepository(context)).also { instance = it }
            }
    }
}
