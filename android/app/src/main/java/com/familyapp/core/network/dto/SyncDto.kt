package com.familyapp.core.network.dto

import com.google.gson.annotations.SerializedName

data class BatchSyncItemsRequestDto(
    @SerializedName("items") val items: List<ItemDto>,
    @SerializedName("since") val since: String? = null
)

data class BatchSyncItemsResponseDto(
    @SerializedName("synced_ids") val syncedIds: List<String>,
    @SerializedName("server_items") val serverItems: List<ItemDto>
)

data class BatchSyncEventsRequestDto(
    @SerializedName("events") val events: List<CalendarEventDto>,
    @SerializedName("since") val since: String? = null
)

data class BatchSyncEventsResponseDto(
    @SerializedName("synced_ids") val syncedIds: List<String>,
    @SerializedName("server_events") val serverEvents: List<CalendarEventDto>
)
