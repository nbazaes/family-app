package com.familyapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.familyapp.core.database.entity.ItemEntity
import com.familyapp.core.database.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE family_id = :familyId AND type = :type AND deleted_at IS NULL ORDER BY created_at DESC")
    fun getItemsByType(familyId: String, type: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE sync_status = 'PENDING_MUTATION'")
    suspend fun getPendingSyncItems(): List<ItemEntity>

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("UPDATE items SET sync_status = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, status: SyncStatus)

    @Query("UPDATE items SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_status = 'PENDING_MUTATION' WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: String, updatedAt: String)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deletePermanently(id: String)
}
