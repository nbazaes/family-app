package com.familyapp.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyapp.core.database.FamilyDatabase
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.core.network.NetworkClient
import com.familyapp.core.network.dto.BatchSyncEventsRequestDto
import com.familyapp.core.network.dto.BatchSyncItemsRequestDto
import com.familyapp.core.network.dto.CalendarEventDto
import com.familyapp.core.network.dto.ItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = FamilyDatabase.getInstance(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = NetworkClient.baseUrl
        val hasToken = NetworkClient.isLoggedIn()
        val familyId = NetworkClient.currentFamilyId
        val api = NetworkClient.apiService

        Log.d("SyncWorker", "Executing SyncWorker -> Host: $baseUrl | LoggedIn: $hasToken | Family: $familyId")

        if (!hasToken) {
            Log.w("SyncWorker", "Skipping sync: No active session. Please log in or register from the server settings icon.")
            return@withContext Result.success()
        }

        try {
            // 1. Sync Items (Shopping & Tasks) - Bidirectional
            val pendingItems = db.itemDao().getPendingSyncItems()
            val itemDtos = pendingItems.map { ItemDto.fromEntity(it) }
            Log.d("SyncWorker", "Syncing items: ${pendingItems.size} pending local mutations to upload")

            val itemsRequest = BatchSyncItemsRequestDto(items = itemDtos)
            val itemsResponse = api.batchSyncItems(itemsRequest)

            if (itemsResponse.isSuccessful && itemsResponse.body() != null) {
                val body = itemsResponse.body()!!
                Log.d("SyncWorker", "Items sync OK: ${body.syncedIds.size} accepted by server, ${body.serverItems.size} items received from server")
                if (body.syncedIds.isNotEmpty()) {
                    db.itemDao().updateSyncStatus(body.syncedIds, SyncStatus.SYNCED)
                }
                if (body.serverItems.isNotEmpty()) {
                    val serverEntities = body.serverItems.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                    db.itemDao().upsertAll(serverEntities)
                }
            } else {
                val errorBody = itemsResponse.errorBody()?.string()
                Log.e("SyncWorker", "Items sync failed with HTTP ${itemsResponse.code()}: $errorBody")
                if (itemsResponse.code() == 401) {
                    return@withContext Result.failure()
                }
            }

            // 2. Sync Calendar Events - Bidirectional
            val pendingEvents = db.calendarEventDao().getPendingSyncEvents()
            val eventDtos = pendingEvents.map { CalendarEventDto.fromEntity(it) }
            Log.d("SyncWorker", "Syncing calendar: ${pendingEvents.size} pending local events to upload")

            val eventsRequest = BatchSyncEventsRequestDto(events = eventDtos)
            val eventsResponse = api.batchSyncEvents(eventsRequest)

            if (eventsResponse.isSuccessful && eventsResponse.body() != null) {
                val body = eventsResponse.body()!!
                Log.d("SyncWorker", "Events sync OK: ${body.syncedIds.size} accepted by server, ${body.serverEvents.size} events received from server")
                if (body.syncedIds.isNotEmpty()) {
                    db.calendarEventDao().updateSyncStatus(body.syncedIds, SyncStatus.SYNCED)
                }
                if (body.serverEvents.isNotEmpty()) {
                    val serverEntities = body.serverEvents.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                    db.calendarEventDao().upsertAll(serverEntities)
                }
            } else {
                val errorBody = eventsResponse.errorBody()?.string()
                Log.e("SyncWorker", "Events sync failed with HTTP ${eventsResponse.code()}: $errorBody")
                if (eventsResponse.code() == 401) {
                    return@withContext Result.failure()
                }
            }

            Log.d("SyncWorker", "Batch sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Batch sync encountered network error: ${e.message}", e)
            Result.retry()
        }
    }
}
