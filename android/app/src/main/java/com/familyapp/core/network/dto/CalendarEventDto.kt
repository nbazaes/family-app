package com.familyapp.core.network.dto

import com.familyapp.core.database.entity.CalendarEventEntity
import com.familyapp.core.database.entity.SyncStatus
import com.google.gson.annotations.SerializedName

data class CalendarEventDto(
    @SerializedName("id") val id: String,
    @SerializedName("family_id") val familyId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("is_all_day") val isAllDay: Boolean = false,
    @SerializedName("color_hex") val colorHex: String = "#4285F4",
    @SerializedName("created_by") val createdBy: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
) {
    fun toEntity(defaultFamilyId: String, syncStatus: SyncStatus = SyncStatus.SYNCED): CalendarEventEntity {
        return CalendarEventEntity(
            id = id,
            familyId = familyId ?: defaultFamilyId,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            colorHex = colorHex,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncStatus = syncStatus
        )
    }

    companion object {
        fun fromEntity(entity: CalendarEventEntity): CalendarEventDto {
            return CalendarEventDto(
                id = entity.id,
                familyId = entity.familyId,
                title = entity.title,
                description = entity.description,
                startTime = entity.startTime,
                endTime = entity.endTime,
                isAllDay = entity.isAllDay,
                colorHex = entity.colorHex,
                createdBy = entity.createdBy,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
        }
    }
}
