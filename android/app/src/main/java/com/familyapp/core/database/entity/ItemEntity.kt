package com.familyapp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [
        Index(value = ["family_id", "type"]),
        Index(value = ["sync_status"])
    ]
)
data class ItemEntity(
    @PrimaryKey
    val id: String, // UUIDv4 generated locally or by server
    @ColumnInfo(name = "family_id")
    val familyId: String,
    val type: String, // "SHOPPING" | "TASK"
    val title: String,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at")
    val completedAt: String? = null,
    
    // Shopping fields
    val quantity: String? = null,
    val category: String? = null, // e.g., "Lácteos", "Limpieza"

    // Task fields
    @ColumnInfo(name = "assigned_to")
    val assignedTo: String? = null,
    @ColumnInfo(name = "due_date")
    val dueDate: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: String? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
