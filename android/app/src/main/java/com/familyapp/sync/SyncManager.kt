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
import com.familyapp.core.network.dto.CalendarEventDto
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

enum class SyncUiState {
    IDLE,
    SYNCING,
    OFFLINE
}

class SyncManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val sseClient = SseClient()
    private val db = FamilyDatabase.getInstance(context)
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    private val _syncUiState = MutableStateFlow(SyncUiState.IDLE)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    private var sseJob: Job? = null

    init {
        // Observe WorkManager batch sync status to reflect real syncing progress in the UI
        coroutineScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("FamilyBatchSync")
                .collect { workInfos ->
                    val activeWork = workInfos.firstOrNull()
                    if (activeWork != null) {
                        when (activeWork.state) {
                            WorkInfo.State.RUNNING -> {
                                Log.d("SyncManager", "Batch sync is RUNNING")
                                _syncUiState.value = SyncUiState.SYNCING
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                Log.d("SyncManager", "Batch sync SUCCEEDED -> IDLE")
                                _syncUiState.value = SyncUiState.IDLE
                            }
                            WorkInfo.State.FAILED -> {
                                Log.w("SyncManager", "Batch sync FAILED -> OFFLINE")
                                _syncUiState.value = SyncUiState.OFFLINE
                            }
                            WorkInfo.State.ENQUEUED -> {
                                Log.d("SyncManager", "Batch sync ENQUEUED")
                                _syncUiState.value = SyncUiState.SYNCING
                            }
                            WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {
                                _syncUiState.value = SyncUiState.IDLE
                            }
                        }
                    } else {
                        _syncUiState.value = SyncUiState.IDLE
                    }
                }
        }
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
                            try {
                                val itemDto = gson.fromJson(sseEvent.data, ItemDto::class.java)
                                if (itemDto != null) {
                                    db.itemDao().upsert(
                                        itemDto.toEntity(NetworkClient.currentFamilyId, SyncStatus.SYNCED)
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error parsing SSE item: ${e.message}")
                            }
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
                        }
                        "event_created", "event_updated" -> {
                            try {
                                val eventDto = gson.fromJson(sseEvent.data, CalendarEventDto::class.java)
                                if (eventDto != null) {
                                    db.calendarEventDao().upsert(
                                        eventDto.toEntity(NetworkClient.currentFamilyId, SyncStatus.SYNCED)
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error parsing SSE event: ${e.message}")
                            }
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
