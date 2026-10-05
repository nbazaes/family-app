package com.familyapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.familyapp.core.database.entity.CalendarEventEntity
import com.familyapp.core.database.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("""
        SELECT * FROM calendar_events 
        WHERE family_id = :familyId 
          AND deleted_at IS NULL 
          AND end_time >= :start 
          AND start_time <= :end 
        ORDER BY start_time ASC
    """)
    fun getEventsInRange(familyId: String, start: String, end: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE family_id = :familyId AND deleted_at IS NULL ORDER BY start_time ASC")
    fun getAllEvents(familyId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE sync_status = 'PENDING_MUTATION'")
    suspend fun getPendingSyncEvents(): List<CalendarEventEntity>

    @Upsert
    suspend fun upsert(event: CalendarEventEntity)

    @Upsert
    suspend fun upsertAll(events: List<CalendarEventEntity>)

    @Query("UPDATE calendar_events SET sync_status = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, status: SyncStatus)

    @Query("UPDATE calendar_events SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_status = 'PENDING_MUTATION' WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: String, updatedAt: String)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deletePermanently(id: String)
}
