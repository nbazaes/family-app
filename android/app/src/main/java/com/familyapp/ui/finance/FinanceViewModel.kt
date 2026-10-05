package com.familyapp.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyapp.core.database.entity.AccountEntity
import com.familyapp.core.database.entity.FinanceTransactionEntity
import com.familyapp.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AccountWithBalance(
    val account: AccountEntity,
    val currentBalance: Double
)

data class FinanceUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val generalBalance: Double = 0.0,
    val totalExpensesMonth: Double = 0.0,
    val selectedAccountId: String? = null,
    val selectedCategory: String? = null,
    val filteredTransactions: List<FinanceTransactionEntity> = emptyList(),
    val groupedTransactions: Map<String, List<FinanceTransactionEntity>> = emptyMap(),
    val isLoading: Boolean = false
)

val FinanceCategories = listOf(
    "🛒 Supermercado" to "Supermercado",
    "💡 Servicios" to "Servicios",
    "🚗 Transporte" to "Transporte",
    "🍽️ Restaurante" to "Restaurante",
    "💊 Salud" to "Salud",
    "🏠 Hogar" to "Hogar",
    "🍿 Ocio" to "Ocio",
    "📚 Educación" to "Educación",
    "🏷️ Otros" to "Otros"
)

class FinanceViewModel(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    private val _selectedCategory = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FinanceUiState> = combine(
        repository.getAccounts(),
        repository.getTransactions(),
        _selectedAccountId,
        _selectedCategory
    ) { accounts, transactions, selectedAccId, selectedCat ->
        // If no accounts exist yet, seed default accounts once
        if (accounts.isEmpty()) {
            repository.ensureDefaultAccounts()
        }

        // Calculate balances per account
        val accountsWithBalance = accounts.map { acc ->
            val accTxs = transactions.filter { it.accountId == acc.id }
            val expenses = accTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val incomes = accTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
            val bal = acc.initialBalance - expenses + incomes
            AccountWithBalance(account = acc, currentBalance = bal)
        }

        val generalBalance = accountsWithBalance.sumOf { it.currentBalance }

        // Current month expenses
        val currentMonthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val totalExpensesMonth = transactions
            .filter { it.type == "EXPENSE" && it.date.startsWith(currentMonthPrefix) }
            .sumOf { it.amount }

        // Filter transactions
        val filtered = transactions.filter { tx ->
            val matchesAccount = selectedAccId == null || tx.accountId == selectedAccId
            val matchesCategory = selectedCat == null || tx.category == selectedCat
            matchesAccount && matchesCategory
        }

        // Group by human readable date
        val grouped = groupTransactionsByHumanDate(filtered)

        FinanceUiState(
            accounts = accountsWithBalance,
            generalBalance = generalBalance,
            totalExpensesMonth = totalExpensesMonth,
            selectedAccountId = selectedAccId,
            selectedCategory = selectedCat,
            filteredTransactions = filtered,
            groupedTransactions = grouped,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState(isLoading = true)
    )

    fun selectAccountFilter(accountId: String?) {
        _selectedAccountId.value = if (_selectedAccountId.value == accountId) null else accountId
    }

    fun selectCategoryFilter(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun addExpense(
        accountId: String,
        amount: Double,
        category: String,
        description: String,
        date: String,
        type: String = "EXPENSE"
    ) {
        viewModelScope.launch {
            if (amount > 0 && accountId.isNotBlank()) {
                repository.addTransaction(
                    accountId = accountId,
                    amount = amount,
                    category = category.ifBlank { "Otros" },
                    description = description.trim().ifBlank { category },
                    date = date,
                    type = type
                )
            }
        }
    }

    fun addAccount(name: String, initialBalance: Double, colorHex: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.addAccount(
                    name = name.trim(),
                    initialBalance = initialBalance,
                    colorHex = colorHex
                )
            }
        }
    }

    fun deleteTransaction(tx: FinanceTransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
        }
    }

    fun deleteAccount(acc: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(acc)
        }
    }

    private fun groupTransactionsByHumanDate(txs: List<FinanceTransactionEntity>): Map<String, List<FinanceTransactionEntity>> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "ES"))

        return txs.groupBy { tx ->
            val dateStr = tx.date.take(10)
            try {
                val parsed = LocalDate.parse(dateStr)
                when (parsed) {
                    today -> "Hoy"
                    yesterday -> "Ayer"
                    else -> parsed.format(dateFormatter).replaceFirstChar { it.uppercase() }
                }
            } catch (e: Exception) {
                dateStr
            }
        }
    }
}
