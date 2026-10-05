package com.familyapp.core.network

import com.familyapp.core.network.dto.BatchSyncEventsRequestDto
import com.familyapp.core.network.dto.BatchSyncEventsResponseDto
import com.familyapp.core.network.dto.BatchSyncItemsRequestDto
import com.familyapp.core.network.dto.BatchSyncItemsResponseDto
import com.familyapp.core.network.dto.CalendarEventDto
import com.familyapp.core.network.dto.ConnectRequestDto
import com.familyapp.core.network.dto.ItemDto
import com.familyapp.core.network.dto.LoginRequestDto
import com.familyapp.core.network.dto.RegisterRequestDto
import com.familyapp.core.network.dto.TokenResponseDto
import com.familyapp.core.network.dto.UserDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // Health
    @GET("api/health")
    suspend fun checkHealth(): Response<Map<String, String>>

    // Zero-Account Instant Family Connect
    @POST("api/auth/connect")
    suspend fun connectFamily(@Body payload: ConnectRequestDto): Response<TokenResponseDto>

    // Traditional Auth
    @POST("api/auth/login")
    suspend fun login(@Body payload: LoginRequestDto): Response<TokenResponseDto>

    @POST("api/auth/register")
    suspend fun register(@Body payload: RegisterRequestDto): Response<TokenResponseDto>

    @GET("api/auth/me")
    suspend fun getMe(): Response<UserDto>

    // Items (Shopping & Tasks)
    @GET("api/items")
    suspend fun getItems(
        @Query("type") type: String? = null,
        @Query("include_completed") includeCompleted: Boolean = true
    ): Response<List<ItemDto>>

    @POST("api/items/batch-sync")
    suspend fun batchSyncItems(
        @Body payload: BatchSyncItemsRequestDto
    ): Response<BatchSyncItemsResponseDto>

    @PATCH("api/items/{id}")
    suspend fun updateItem(
        @Path("id") id: String,
        @Body payload: Map<String, Any?>
    ): Response<ItemDto>

    @DELETE("api/items/{id}")
    suspend fun deleteItem(
        @Path("id") id: String
    ): Response<Unit>

    // Calendar Events
    @GET("api/calendar/events")
    suspend fun getCalendarEvents(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null
    ): Response<List<CalendarEventDto>>

    @POST("api/calendar/events/batch-sync")
    suspend fun batchSyncEvents(
        @Body payload: BatchSyncEventsRequestDto
    ): Response<BatchSyncEventsResponseDto>

    @PATCH("api/calendar/events/{id}")
    suspend fun updateCalendarEvent(
        @Path("id") id: String,
        @Body payload: Map<String, Any?>
    ): Response<CalendarEventDto>

    @DELETE("api/calendar/events/{id}")
    suspend fun deleteCalendarEvent(
        @Path("id") id: String
    ): Response<Unit>

    // Export .ics
    @GET("api/calendar/export.ics")
    suspend fun exportCalendar(): Response<ResponseBody>
}
