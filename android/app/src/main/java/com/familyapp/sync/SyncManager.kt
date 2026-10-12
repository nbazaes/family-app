package com.familyapp.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.familyapp.core.database.FamilyDatabase
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.core.network.NetworkClient
import com.familyapp.core.network.dto.AccountDto
import com.familyapp.core.network.dto.CalendarEventDto
import com.familyapp.core.network.dto.FinanceTransactionDto
import com.familyapp.core.network.dto.ItemDto
import com.familyapp.core.network.sse.SseClient
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class SyncUiState {
    object IDLE : SyncUiState()
    object SYNCING : SyncUiState()
    object OFFLINE : SyncUiState()
    data class ERROR(
        val title: String,
        val message: String,
        val solution: String,
        val failedModule: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncUiState()
}

data class SyncErrorDetails(
    val title: String,
    val message: String,
    val solution: String,
    val failedModule: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class SyncManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val sseClient = SseClient()
    private val db = FamilyDatabase.getInstance(context)
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    private val _syncUiState = MutableStateFlow<SyncUiState>(SyncUiState.IDLE)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    private val _currentError = MutableStateFlow<SyncErrorDetails?>(null)
    val currentError: StateFlow<SyncErrorDetails?> = _currentError.asStateFlow()

    private var sseJob: Job? = null

    init {
        // Observe WorkManager batch sync status to reflect real syncing progress in the UI
        coroutineScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("FamilyBatchSync")
                .collect { workInfos ->
                    val activeWork = workInfos.firstOrNull()
                    if (activeWork != null) {
                        when (activeWork.state) {
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                                Log.d("SyncManager", "Batch sync is RUNNING/ENQUEUED")
                                _syncUiState.value = SyncUiState.SYNCING
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                val err = _currentError.value
                                if (err != null) {
                                    Log.w("SyncManager", "Batch sync finished with recorded error: ${err.title}")
                                    _syncUiState.value = SyncUiState.ERROR(
                                        title = err.title,
                                        message = err.message,
                                        solution = err.solution,
                                        failedModule = err.failedModule,
                                        timestamp = err.timestamp
                                    )
                                } else {
                                    Log.d("SyncManager", "Batch sync SUCCEEDED -> IDLE")
                                    _syncUiState.value = SyncUiState.IDLE
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                val err = _currentError.value
                                if (err != null) {
                                    Log.w("SyncManager", "Batch sync FAILED with error: ${err.title}")
                                    _syncUiState.value = SyncUiState.ERROR(
                                        title = err.title,
                                        message = err.message,
                                        solution = err.solution,
                                        failedModule = err.failedModule,
                                        timestamp = err.timestamp
                                    )
                                } else {
                                    Log.w("SyncManager", "Batch sync FAILED -> OFFLINE")
                                    _syncUiState.value = SyncUiState.OFFLINE
                                }
                            }
                            WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {
                                val err = _currentError.value
                                if (err != null) {
                                    _syncUiState.value = SyncUiState.ERROR(
                                        title = err.title,
                                        message = err.message,
                                        solution = err.solution,
                                        failedModule = err.failedModule,
                                        timestamp = err.timestamp
                                    )
                                } else {
                                    _syncUiState.value = SyncUiState.IDLE
                                }
                            }
                        }
                    } else {
                        val err = _currentError.value
                        if (err != null) {
                            _syncUiState.value = SyncUiState.ERROR(
                                title = err.title,
                                message = err.message,
                                solution = err.solution,
                                failedModule = err.failedModule,
                                timestamp = err.timestamp
                            )
                        } else {
                            _syncUiState.value = SyncUiState.IDLE
                        }
                    }
                }
        }
    }

    fun recordSyncError(error: SyncErrorDetails) {
        _currentError.value = error
        _syncUiState.value = SyncUiState.ERROR(
            title = error.title,
            message = error.message,
            solution = error.solution,
            failedModule = error.failedModule,
            timestamp = error.timestamp
        )
    }

    fun clearSyncError() {
        _currentError.value = null
        _syncUiState.value = SyncUiState.IDLE
    }

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "FamilyBatchSync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    fun startRealtimeSync() {
        if (!NetworkClient.isLoggedIn()) {
            Log.d("SyncManager", "Skipping SSE: user is not logged in")
            return
        }

        if (sseJob?.isActive == true) return

        sseJob = coroutineScope.launch {
            val token = NetworkClient.authToken
            Log.d("SyncManager", "Starting SSE stream for family: ${NetworkClient.currentFamilyId}")

            sseClient.listenToEvents(token)
                .catch { e ->
                    Log.e("SyncManager", "SSE Stream error: ${e.message}", e)
                }
                .collect { sseEvent ->
                    when (sseEvent.type) {
                        "connected" -> {
                            Log.d("SyncManager", "SSE Connected to family channel")
                        }
                        "item_created", "item_updated" -> {
                            Log.d("SyncManager", "SSE item mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "item_deleted" -> {
                            try {
                                val itemDto = gson.fromJson(sseEvent.data, ItemDto::class.java)
                                if (itemDto?.id != null) {
                                    db.itemDao().deletePermanently(itemDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling item delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                        "event_created", "event_updated" -> {
                            Log.d("SyncManager", "SSE event mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "event_deleted" -> {
                            try {
                                val eventDto = gson.fromJson(sseEvent.data, CalendarEventDto::class.java)
                                if (eventDto?.id != null) {
                                    db.calendarEventDao().deletePermanently(eventDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling calendar event delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                        "account_created", "account_updated" -> {
                            Log.d("SyncManager", "SSE account mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "account_deleted" -> {
                            try {
                                val accDto = gson.fromJson(sseEvent.data, AccountDto::class.java)
                                if (accDto?.id != null) {
                                    db.accountDao().deletePermanently(accDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling account delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                        "finance_transaction_created", "finance_transaction_updated" -> {
                            Log.d("SyncManager", "SSE finance transaction mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "finance_transaction_deleted" -> {
                            try {
                                val txDto = gson.fromJson(sseEvent.data, FinanceTransactionDto::class.java)
                                if (txDto?.id != null) {
                                    db.financeTransactionDao().deletePermanently(txDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling finance transaction delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                    }
                }
        }
    }

    fun restartRealtimeSync() {
        stopRealtimeSync()
        startRealtimeSync()
    }

    fun stopRealtimeSync() {
        sseJob?.cancel()
        sseJob = null
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
