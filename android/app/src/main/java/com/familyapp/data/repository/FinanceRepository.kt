package com.familyapp.data.repository

import com.familyapp.core.database.dao.AccountDao
import com.familyapp.core.database.dao.FinanceTransactionDao
import com.familyapp.core.database.entity.AccountEntity
import com.familyapp.core.database.entity.FinanceTransactionEntity
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.core.network.NetworkClient
import com.familyapp.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class FinanceRepository(
    private val accountDao: AccountDao,
    private val transactionDao: FinanceTransactionDao,
    private val syncManager: SyncManager
) {
    fun getAccounts(): Flow<List<AccountEntity>> {
        return accountDao.getAccounts(NetworkClient.currentFamilyId)
    }

    fun getTransactions(): Flow<List<FinanceTransactionEntity>> {
        return transactionDao.getTransactions(NetworkClient.currentFamilyId)
    }

    suspend fun addAccount(
        name: String,
        initialBalance: Double = 0.0,
        colorHex: String = "#1E523A"
    ): String {
        val now = Instant.now().toString()
        val accountId = UUID.randomUUID().toString()
        val account = AccountEntity(
            id = accountId,
            familyId = NetworkClient.currentFamilyId,
            name = name,
            initialBalance = initialBalance,
            colorHex = colorHex,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        accountDao.upsert(account)
        syncManager.scheduleSync()
        return accountId
    }

    suspend fun updateAccount(
        account: AccountEntity,
        name: String,
        initialBalance: Double,
        colorHex: String
    ) {
        val now = Instant.now().toString()
        val updated = account.copy(
            name = name,
            initialBalance = initialBalance,
            colorHex = colorHex,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        accountDao.upsert(updated)
        syncManager.scheduleSync()
    }

    suspend fun deleteAccount(account: AccountEntity) {
        val now = Instant.now().toString()
        accountDao.markDeleted(account.id, deletedAt = now, updatedAt = now)
        syncManager.scheduleSync()
    }

    suspend fun addTransaction(
        accountId: String,
        amount: Double,
        category: String,
        description: String,
        date: String,
        type: String = "EXPENSE",
        createdBy: String = NetworkClient.currentUserName ?: "Familiar"
    ): String {
        val now = Instant.now().toString()
        val txId = UUID.randomUUID().toString()
        val tx = FinanceTransactionEntity(
            id = txId,
            familyId = NetworkClient.currentFamilyId,
            accountId = accountId,
            amount = amount,
            category = category,
            description = description,
            date = date,
            type = type,
            createdBy = createdBy,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        transactionDao.upsert(tx)
        syncManager.scheduleSync()
        return txId
    }

    suspend fun deleteTransaction(tx: FinanceTransactionEntity) {
        val now = Instant.now().toString()
        transactionDao.markDeleted(tx.id, deletedAt = now, updatedAt = now)
        syncManager.scheduleSync()
    }

    suspend fun ensureDefaultAccounts() {
        val existing = accountDao.getPendingSyncAccounts()
        // If no accounts yet, add defaults
        val defaultList = listOf(
            Triple("Efectivo", 0.0, "#1E523A"),
            Triple("Banco", 0.0, "#1976D2")
        )
        for (def in defaultList) {
            addAccount(def.first, def.second, def.third)
        }
    }
}
