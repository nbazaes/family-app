package com.familyapp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "finance_transactions",
    indices = [
        Index(value = ["family_id", "date"]),
        Index(value = ["account_id"]),
        Index(value = ["sync_status"])
    ]
)
data class FinanceTransactionEntity(
    @PrimaryKey
    val id: String, // UUIDv4
    @ColumnInfo(name = "family_id")
    val familyId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    val amount: Double,
    val category: String,
    val description: String,
    val date: String, // ISO-8601 string
    val type: String = "EXPENSE", // "EXPENSE" | "INCOME"
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
