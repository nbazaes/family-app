package com.familyapp.data.repository

import com.familyapp.core.database.dao.CalendarEventDao
import com.familyapp.core.database.entity.CalendarEventEntity
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.core.network.NetworkClient
import com.familyapp.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class CalendarRepository(
    private val calendarEventDao: CalendarEventDao,
    private val syncManager: SyncManager
) {
    fun getEventsInRange(start: String, end: String): Flow<List<CalendarEventEntity>> {
        return calendarEventDao.getEventsInRange(NetworkClient.currentFamilyId, start, end)
    }

    fun getAllEvents(): Flow<List<CalendarEventEntity>> {
        return calendarEventDao.getAllEvents(NetworkClient.currentFamilyId)
    }

    suspend fun addEvent(
        title: String,
        description: String? = null,
        startTime: String,
        endTime: String,
        isAllDay: Boolean = false,
        colorHex: String = "#4285F4",
        createdBy: String = "Familiar"
    ) {
        val now = Instant.now().toString()
        val event = CalendarEventEntity(
            id = UUID.randomUUID().toString(),
            familyId = NetworkClient.currentFamilyId,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            colorHex = colorHex,
            createdBy = createdBy,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        calendarEventDao.upsert(event)
        syncManager.scheduleSync()
    }

    suspend fun deleteEvent(event: CalendarEventEntity) {
        val now = Instant.now().toString()
        calendarEventDao.markDeleted(event.id, deletedAt = now, updatedAt = now)
        syncManager.scheduleSync()
    }

    suspend fun restoreEvent(event: CalendarEventEntity) {
        val now = Instant.now().toString()
        val restored = event.copy(
            deletedAt = null,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        calendarEventDao.upsert(restored)
        syncManager.scheduleSync()
    }

    fun getCalendarFeedUrl(): String {

        val base = NetworkClient.baseUrl.removeSuffix("/")
        val token = NetworkClient.authToken ?: ""
        return "$base/api/calendar/feed.ics?token=$token"
    }
}
