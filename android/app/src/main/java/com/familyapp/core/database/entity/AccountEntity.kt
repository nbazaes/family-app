package com.familyapp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["family_id"]),
        Index(value = ["sync_status"])
    ]
)
data class AccountEntity(
    @PrimaryKey
    val id: String, // UUIDv4
    @ColumnInfo(name = "family_id")
    val familyId: String,
    val name: String,
    @ColumnInfo(name = "initial_balance")
    val initialBalance: Double = 0.0,
    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#1E523A",

    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: String? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
