package com.familyapp.core.network.dto

import com.familyapp.core.database.entity.ItemEntity
import com.familyapp.core.database.entity.SyncStatus
import com.google.gson.annotations.SerializedName

data class ItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("family_id") val familyId: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("quantity") val quantity: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("assigned_to") val assignedTo: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
) {
    fun toEntity(defaultFamilyId: String, syncStatus: SyncStatus = SyncStatus.SYNCED): ItemEntity {
        return ItemEntity(
            id = id,
            familyId = familyId ?: defaultFamilyId,
            type = type,
            title = title,
            isCompleted = isCompleted,
            completedAt = completedAt,
            quantity = quantity,
            category = category,
            assignedTo = assignedTo,
            dueDate = dueDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncStatus = syncStatus
        )
    }

    companion object {
        fun fromEntity(entity: ItemEntity): ItemDto {
            return ItemDto(
                id = entity.id,
                familyId = entity.familyId,
                type = entity.type,
                title = entity.title,
                isCompleted = entity.isCompleted,
                completedAt = entity.completedAt,
                quantity = entity.quantity,
                category = entity.category,
                assignedTo = entity.assignedTo,
                dueDate = entity.dueDate,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
        }
    }
}
