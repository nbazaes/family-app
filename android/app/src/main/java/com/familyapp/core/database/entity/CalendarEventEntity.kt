package com.familyapp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["family_id", "start_time", "end_time"]),
        Index(value = ["sync_status"])
    ]
)
data class CalendarEventEntity(
    @PrimaryKey
    val id: String, // UUIDv4
    @ColumnInfo(name = "family_id")
    val familyId: String,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "start_time")
    val startTime: String,
    @ColumnInfo(name = "end_time")
    val endTime: String,
    @ColumnInfo(name = "is_all_day")
    val isAllDay: Boolean = false,
    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#4285F4",
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: String? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
