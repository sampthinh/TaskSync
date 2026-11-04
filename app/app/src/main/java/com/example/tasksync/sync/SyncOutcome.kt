package com.example.tasksync.sync

enum class SyncOutcome {
    SUCCESS,
    FAILED,
    RETRY
}

enum class SyncUiState { IDLE, WAITING, RUNNING, SUCCEEDED, FAILED }
