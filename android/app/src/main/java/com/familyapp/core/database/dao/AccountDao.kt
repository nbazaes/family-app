package com.familyapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.familyapp.core.database.entity.AccountEntity
import com.familyapp.core.database.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE family_id = :familyId AND deleted_at IS NULL ORDER BY created_at ASC")
    fun getAccounts(familyId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE sync_status = 'PENDING_MUTATION'")
    suspend fun getPendingSyncAccounts(): List<AccountEntity>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET sync_status = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, status: SyncStatus)

    @Query("UPDATE accounts SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_status = 'PENDING_MUTATION' WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: String, updatedAt: String)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deletePermanently(id: String)
}
