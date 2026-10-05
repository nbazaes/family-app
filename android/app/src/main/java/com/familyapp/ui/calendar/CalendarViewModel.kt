package com.familyapp.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyapp.core.database.entity.CalendarEventEntity
import com.familyapp.core.network.NetworkClient
import com.familyapp.data.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedMemberFilter: String = "ALL", // "ALL", "MINE", or member name
    val members: List<String> = emptyList(),
    val currentUserName: String? = null,
    val selectedDateEvents: List<CalendarEventEntity> = emptyList(),
    val otherUpcomingEvents: Map<String, List<CalendarEventEntity>> = emptyMap(),
    val datesWithEvents: Set<String> = emptySet(),
    val totalEventsCount: Int = 0,
    val feedUrl: String = "",
    val isLoading: Boolean = false
)

class CalendarViewModel(
    private val repository: CalendarRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _selectedMemberFilter = MutableStateFlow("ALL")

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.getAllEvents(),
        _selectedDate,
        _selectedMemberFilter
    ) { allEvents, selectedDate, memberFilter ->
        val currentUserName = NetworkClient.currentUserName?.trim()?.ifBlank { null }

        // Distinct family members who create or participate in events
        val allCreators = allEvents.mapNotNull { it.createdBy.trim().ifBlank { null } }.distinct()
        val membersList = buildList {
            currentUserName?.let { add(it) }
            allCreators.forEach { if (!contains(it)) add(it) }
        }

        // Apply member filter
        val filteredEvents = when {
            memberFilter == "MINE" && currentUserName != null -> {
                allEvents.filter { it.createdBy.equals(currentUserName, ignoreCase = true) }
            }
            memberFilter != "ALL" && memberFilter != "MINE" -> {
                allEvents.filter { it.createdBy.equals(memberFilter, ignoreCase = true) }
            }
            else -> allEvents
        }

        // Dates that have at least one event
        val datesWithEvents = filteredEvents.map { it.startTime.take(10) }.toSet()

        val selectedDateStr = selectedDate.toString()
        val selectedDateEvents = filteredEvents.filter { it.startTime.startsWith(selectedDateStr) }

        // Other upcoming events after selectedDate
        val upcomingGrouped = filteredEvents
            .filter { it.startTime.take(10) > selectedDateStr }
            .groupBy { it.startTime.take(10) }
            .toSortedMap()

        CalendarUiState(
            selectedDate = selectedDate,
            selectedMemberFilter = memberFilter,
            members = membersList,
            currentUserName = currentUserName,
            selectedDateEvents = selectedDateEvents,
            otherUpcomingEvents = upcomingGrouped,
            datesWithEvents = datesWithEvents,
            totalEventsCount = filteredEvents.size,
            feedUrl = repository.getCalendarFeedUrl(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState(
            isLoading = true,
            feedUrl = repository.getCalendarFeedUrl()
        )
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun selectMemberFilter(filter: String) {
        _selectedMemberFilter.value = filter
    }

    fun addEvent(
        title: String,
        description: String?,
        date: String,
        startTime: String,
        endTime: String,
        isAllDay: Boolean,
        colorHex: String,
        createdBy: String
    ) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                val fullStart = if (isAllDay) "${date}T00:00:00Z" else "${date}T${startTime}:00Z"
                val fullEnd = if (isAllDay) "${date}T23:59:59Z" else "${date}T${endTime}:00Z"

                repository.addEvent(
                    title = title.trim(),
                    description = description?.trim()?.ifBlank { null },
                    startTime = fullStart,
                    endTime = fullEnd,
                    isAllDay = isAllDay,
                    colorHex = colorHex,
                    createdBy = createdBy.trim().ifBlank { "Familiar" }
                )
            }
        }
    }

    fun deleteEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun restoreEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.restoreEvent(event)
        }
    }
}
