package com.example.tasksync.data.local

import androidx.room.TypeConverter
import com.example.tasksync.data.model.SyncStatus

/** Room chi luu duoc chu/so, nen doi SyncStatus <-> chu. */
class Converters {
    @TypeConverter
    fun fromStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
