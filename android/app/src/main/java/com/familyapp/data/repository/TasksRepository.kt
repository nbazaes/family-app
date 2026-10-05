package com.familyapp.data.repository

import com.familyapp.core.database.dao.ItemDao
import com.familyapp.core.database.entity.ItemEntity
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.core.network.NetworkClient
import com.familyapp.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class TasksRepository(
    private val itemDao: ItemDao,
    private val syncManager: SyncManager
) {
    fun getTasks(): Flow<List<ItemEntity>> {
        return itemDao.getItemsByType(NetworkClient.currentFamilyId, "TASK")
    }

    suspend fun addTask(
        title: String,
        assignedTo: String? = null,
        dueDate: String? = null
    ) {
        val now = Instant.now().toString()
        val item = ItemEntity(
            id = UUID.randomUUID().toString(),
            familyId = NetworkClient.currentFamilyId,
            type = "TASK",
            title = title,
            assignedTo = assignedTo,
            dueDate = dueDate,
            isCompleted = false,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        itemDao.upsert(item)
        syncManager.scheduleSync()
    }

    suspend fun toggleCompleted(item: ItemEntity) {
        val now = Instant.now().toString()
        val updated = item.copy(
            isCompleted = !item.isCompleted,
            completedAt = if (!item.isCompleted) now else null,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        itemDao.upsert(updated)
        syncManager.scheduleSync()
    }

    suspend fun deleteTask(item: ItemEntity) {
        val now = Instant.now().toString()
        itemDao.markDeleted(item.id, deletedAt = now, updatedAt = now)
        syncManager.scheduleSync()
    }

    suspend fun restoreTask(item: ItemEntity) {
        val now = Instant.now().toString()
        val restored = item.copy(
            deletedAt = null,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_MUTATION
        )
        itemDao.upsert(restored)
        syncManager.scheduleSync()
    }
}

