package com.familyapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.familyapp.core.database.entity.FinanceTransactionEntity
import com.familyapp.core.database.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceTransactionDao {
    @Query("SELECT * FROM finance_transactions WHERE family_id = :familyId AND deleted_at IS NULL ORDER BY date DESC, created_at DESC")
    fun getTransactions(familyId: String): Flow<List<FinanceTransactionEntity>>

    @Query("SELECT * FROM finance_transactions WHERE family_id = :familyId AND account_id = :accountId AND deleted_at IS NULL ORDER BY date DESC, created_at DESC")
    fun getTransactionsByAccount(familyId: String, accountId: String): Flow<List<FinanceTransactionEntity>>

    @Query("SELECT * FROM finance_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): FinanceTransactionEntity?

    @Query("SELECT * FROM finance_transactions WHERE sync_status = 'PENDING_MUTATION'")
    suspend fun getPendingSyncTransactions(): List<FinanceTransactionEntity>

    @Upsert
    suspend fun upsert(transaction: FinanceTransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<FinanceTransactionEntity>)

    @Query("UPDATE finance_transactions SET sync_status = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, status: SyncStatus)

    @Query("UPDATE finance_transactions SET deleted_at = :deletedAt, updated_at = :updatedAt, sync_status = 'PENDING_MUTATION' WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: String, updatedAt: String)

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deletePermanently(id: String)
}
