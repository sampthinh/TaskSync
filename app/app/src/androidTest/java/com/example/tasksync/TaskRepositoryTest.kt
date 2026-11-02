package com.example.tasksync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tasksync.data.local.AppDatabase
import com.example.tasksync.data.model.SyncStatus
import com.example.tasksync.data.model.Task
import com.example.tasksync.data.repository.ConflictResult
import com.example.tasksync.data.repository.TaskRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Kiem tra tang du lieu (Room + Repository) khong can mang, khong can server. */
@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repo = TaskRepositoryImpl(db.taskDao(), prefs)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun firstPending(): Task = repo.getPendingTasks().first()

    @Test
    fun addTask_savedAsPendingWithVersionZero() = runBlocking {
        repo.addTask("Hoc bai", "Lam bao cao")
        val list = repo.observeTasks().first()
        assertEquals(1, list.size)
        assertEquals("Hoc bai", list[0].title)
        assertEquals(SyncStatus.PENDING, list[0].syncStatus)
        assertEquals(0, list[0].version)
    }

    @Test
    fun updateAndComplete_stayOfflineAndPending() = runBlocking {
        repo.addTask("A", "")
        val id = firstPending().id
        repo.updateTask(id, "B", "mo ta")
        repo.setCompleted(id, true)
        val t = repo.observeTasks().first()[0]
        assertEquals("B", t.title)
        assertTrue(t.isCompleted)
        assertEquals(SyncStatus.PENDING, t.syncStatus)
    }

    @Test
    fun deleteTask_hiddenFromListButKeptForSync() = runBlocking {
        repo.addTask("A", "")
        val id = firstPending().id
        repo.deleteTask(id)
        assertTrue(repo.observeTasks().first().isEmpty())
        val pending = repo.getPendingTasks()
        assertEquals(1, pending.size)
        assertTrue(pending[0].isDeleted)
    }

    @Test
    fun markSynced_setsSynced() = runBlocking {
        repo.addTask("A", "")
        val t = firstPending()
        repo.markSyncing(listOf(t.id))
        repo.markSynced(t.copy(version = 1, updatedAt = 100L))
        val saved = repo.observeTasks().first()[0]
        assertEquals(SyncStatus.SYNCED, saved.syncStatus)
        assertEquals(1, saved.version)
        assertTrue(repo.getPendingTasks().isEmpty())
    }

    @Test
    fun markSynced_keepsEditMadeDuringSync() = runBlocking {
        repo.addTask("A", "")
        val t = firstPending()
        repo.markSyncing(listOf(t.id))
        repo.updateTask(t.id, "Sua trong luc dong bo", "")   // nguoi dung sua tiep
        repo.markSynced(t.copy(version = 1, updatedAt = 100L)) // server xac nhan ban cu
        val saved = repo.observeTasks().first()[0]
        assertEquals("Sua trong luc dong bo", saved.title)    // khong bi ghi de
        assertEquals(SyncStatus.PENDING, saved.syncStatus)    // van phai gui lai
        assertEquals(1, saved.version)                        // nhung da nhan version moi
    }

    @Test
    fun markFailed_onlyAffectsSyncingTasks() = runBlocking {
        repo.addTask("A", "")
        val t = firstPending()
        repo.markSyncing(listOf(t.id))
        repo.markFailed(listOf(t.id))
        assertEquals(SyncStatus.FAILED, repo.observeTasks().first()[0].syncStatus)
        assertEquals(1, repo.getPendingTasks().size)          // FAILED van duoc thu lai
    }

    @Test
    fun applyServerChanges_insertsNewAndSkipsPending() = runBlocking {
        repo.addTask("May toi sua", "")
        val local = firstPending()
        val fromServerSame = local.copy(title = "Server sua", version = 3, updatedAt = 500L)
        val fromServerNew = Task(id = "new-1", title = "Moi tu server", version = 1, updatedAt = 400L)
        repo.applyServerChanges(listOf(fromServerSame, fromServerNew))
        val list = repo.observeTasks().first()
        assertEquals(2, list.size)
        assertEquals("May toi sua", list.first { it.id == local.id }.title)   // bi bo qua
        assertEquals(SyncStatus.SYNCED, list.first { it.id == "new-1" }.syncStatus)
    }

    @Test
    fun applyServerChanges_deletedTaskNotShown() = runBlocking {
        val deleted = Task(id = "d-1", title = "Da xoa", isDeleted = true, version = 2, updatedAt = 300L)
        repo.applyServerChanges(listOf(deleted))
        assertTrue(repo.observeTasks().first().isEmpty())
    }

    @Test
    fun resolveConflict_lastWriteWins() {
        val local = Task(id = "x", title = "L", localUpdatedAt = 200L)
        val serverOld = Task(id = "x", title = "S", updatedAt = 100L)
        val serverNew = Task(id = "x", title = "S", updatedAt = 300L)
        assertTrue(repo.resolveConflict(local, serverOld) is ConflictResult.KeepLocal)
        assertTrue(repo.resolveConflict(local, serverNew) is ConflictResult.TakeServer)
    }

    @Test
    fun lastSyncTime_persists() = runBlocking {
        assertEquals(0L, repo.getLastSyncTime())
        repo.setLastSyncTime(12345L)
        assertEquals(12345L, repo.getLastSyncTime())
    }
}
