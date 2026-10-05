package com.familyapp.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyapp.core.database.entity.ItemEntity
import com.familyapp.core.network.NetworkClient
import com.familyapp.data.repository.TasksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TaskUrgencyGroups(
    val overdue: List<ItemEntity> = emptyList(),
    val today: List<ItemEntity> = emptyList(),
    val upcoming: List<ItemEntity> = emptyList(),
    val noDate: List<ItemEntity> = emptyList()
) {
    val totalCount: Int
        get() = overdue.size + today.size + upcoming.size + noDate.size
}

data class TasksUiState(
    val pendingGroups: TaskUrgencyGroups = TaskUrgencyGroups(),
    val completedTasks: List<ItemEntity> = emptyList(),
    val members: List<String> = emptyList(),
    val selectedFilter: String = "ALL", // "ALL", "MINE", or specific member name
    val pendingCountAll: Int = 0,
    val pendingCountMine: Int = 0,
    val memberPendingCounts: Map<String, Int> = emptyMap(),
    val currentUserName: String? = null,
    val isLoading: Boolean = false
)

class TasksViewModel(
    private val repository: TasksRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("ALL")

    val uiState: StateFlow<TasksUiState> = combine(
        repository.getTasks(),
        _selectedFilter
    ) { tasks, filter ->
        val currentUserName = NetworkClient.currentUserName?.trim()?.ifBlank { null }
        val pendingAll = tasks.filter { !it.isCompleted }
        val completedAll = tasks.filter { it.isCompleted }

        // Distinct family members
        val allAssigned = tasks.mapNotNull { it.assignedTo?.trim()?.ifBlank { null } }.distinct()
        val membersList = buildList {
            currentUserName?.let { add(it) }
            allAssigned.forEach { if (!contains(it)) add(it) }
        }

        // Pending counts
        val countAll = pendingAll.size
        val countMine = if (currentUserName != null) {
            pendingAll.count { it.assignedTo.equals(currentUserName, ignoreCase = true) }
        } else 0

        val memberCounts = membersList.associateWith { member ->
            pendingAll.count { it.assignedTo.equals(member, ignoreCase = true) }
        }

        // Filter applied
        val filteredPending = when {
            filter == "MINE" && currentUserName != null -> {
                pendingAll.filter { it.assignedTo.equals(currentUserName, ignoreCase = true) }
            }
            filter != "ALL" && filter != "MINE" -> {
                pendingAll.filter { it.assignedTo.equals(filter, ignoreCase = true) }
            }
            else -> pendingAll
        }

        val filteredCompleted = when {
            filter == "MINE" && currentUserName != null -> {
                completedAll.filter { it.assignedTo.equals(currentUserName, ignoreCase = true) }
            }
            filter != "ALL" && filter != "MINE" -> {
                completedAll.filter { it.assignedTo.equals(filter, ignoreCase = true) }
            }
            else -> completedAll
        }

        // Urgency grouping
        val today = LocalDate.now()
        val overdue = mutableListOf<ItemEntity>()
        val todayTasks = mutableListOf<ItemEntity>()
        val upcoming = mutableListOf<ItemEntity>()
        val noDate = mutableListOf<ItemEntity>()

        for (task in filteredPending) {
            val dueStr = task.dueDate?.take(10)
            if (dueStr.isNullOrBlank()) {
                noDate.add(task)
            } else {
                try {
                    val date = LocalDate.parse(dueStr)
                    when {
                        date.isBefore(today) -> overdue.add(task)
                        date.isEqual(today) -> todayTasks.add(task)
                        else -> upcoming.add(task)
                    }
                } catch (e: Exception) {
                    noDate.add(task)
                }
            }
        }

        TasksUiState(
            pendingGroups = TaskUrgencyGroups(
                overdue = overdue,
                today = todayTasks,
                upcoming = upcoming,
                noDate = noDate
            ),
            completedTasks = filteredCompleted,
            members = membersList,
            selectedFilter = filter,
            pendingCountAll = countAll,
            pendingCountMine = countMine,
            memberPendingCounts = memberCounts,
            currentUserName = currentUserName,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasksUiState(isLoading = true)
    )

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun addTask(title: String, assignedTo: String?, dueDate: String?) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                repository.addTask(
                    title = title.trim(),
                    assignedTo = assignedTo?.trim()?.ifBlank { null },
                    dueDate = dueDate?.trim()?.ifBlank { null }
                )
            }
        }
    }

    fun toggleTask(task: ItemEntity) {
        viewModelScope.launch {
            repository.toggleCompleted(task)
        }
    }

    fun deleteTask(task: ItemEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun restoreTask(task: ItemEntity) {
        viewModelScope.launch {
            repository.restoreTask(task)
        }
    }
}
