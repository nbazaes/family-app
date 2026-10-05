package com.familyapp.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyapp.core.database.entity.ItemEntity
import com.familyapp.data.repository.ShoppingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val itemsByCategory: Map<String, List<ItemEntity>> = emptyMap(),
    val totalPending: Int = 0,
    val totalCompleted: Int = 0,
    val isLoading: Boolean = false
)

class ShoppingViewModel(
    private val repository: ShoppingRepository
) : ViewModel() {

    val uiState: StateFlow<ShoppingUiState> = repository.getShoppingItems()
        .map { items ->
            val pending = items.count { !it.isCompleted }
            val completed = items.count { it.isCompleted }
            val grouped = items.groupBy { it.category?.ifBlank { "General" } ?: "General" }
            ShoppingUiState(
                itemsByCategory = grouped,
                totalPending = pending,
                totalCompleted = completed,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShoppingUiState(isLoading = true)
        )

    fun addItem(title: String, quantity: String?, category: String?) {
        viewModelScope.launch {
            if (title.isNotBlank()) {
                repository.addShoppingItem(
                    title = title.trim(),
                    quantity = quantity?.trim()?.ifBlank { null },
                    category = category?.trim()?.ifBlank { "General" } ?: "General"
                )
            }
        }
    }

    fun toggleItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.toggleCompleted(item)
        }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.deleteShoppingItem(item)
        }
    }

    fun restoreItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.restoreShoppingItem(item)
        }
    }
}

