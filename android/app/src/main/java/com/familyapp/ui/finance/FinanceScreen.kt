package com.familyapp.ui.finance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.familyapp.core.database.entity.AccountEntity
import com.familyapp.core.database.entity.FinanceTransactionEntity
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.ui.components.ModalImeBackHandler
import com.familyapp.ui.theme.SyncOrange
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("es", "CL"))
    return "$${formatter.format(amount)}"
}

fun parseColorSafe(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color(0xFF1E523A)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddExpenseSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<FinanceTransactionEntity?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var transactionToDelete by remember { mutableStateOf<FinanceTransactionEntity?>(null) }

    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddExpenseSheet = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo gasto", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 1. Hero General Balance Card
            item {
                GeneralBalanceCard(
                    generalBalance = uiState.generalBalance,
                    monthlyExpenses = uiState.totalExpensesMonth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 2. Accounts Carousel Header & Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cuentas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showAddAccountDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nueva cuenta", fontSize = 13.sp)
                    }
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(uiState.accounts, key = { it.account.id }) { item ->
                        val isSelected = uiState.selectedAccountId == item.account.id
                        AccountCard(
                            accountWithBalance = item,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectAccountFilter(item.account.id)
                            },
                            onEdit = {
                                accountToEdit = item.account
                            }
                        )
                    }
                }
            }

            // 3. Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { viewModel.selectCategoryFilter(null) },
                        label = { Text("Todas") }
                    )
                    FinanceCategories.forEach { (label, catName) ->
                        FilterChip(
                            selected = uiState.selectedCategory == catName,
                            onClick = { viewModel.selectCategoryFilter(catName) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 4. Section Title
            item {
                val filterText = when {
                    uiState.selectedAccountId != null && uiState.selectedCategory != null -> "Movimientos filtrados"
                    uiState.selectedAccountId != null -> {
                        val accName = uiState.accounts.find { it.account.id == uiState.selectedAccountId }?.account?.name ?: ""
                        "Movimientos en $accName"
                    }
                    uiState.selectedCategory != null -> "Movimientos en ${uiState.selectedCategory}"
                    else -> "Movimientos recientes"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Grouped Expenses List
            if (uiState.filteredTransactions.isEmpty()) {
                item {
                    EmptyFinanceState(modifier = Modifier.padding(32.dp))
                }
            } else {
                uiState.groupedTransactions.forEach { (dateGroup, txs) ->
                    item {
                        Text(
                            text = dateGroup,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }

                    items(txs, key = { it.id }) { tx ->
                        val accountName = uiState.accounts.find { it.account.id == tx.accountId }?.account?.name ?: "Cuenta"
                        ExpenseItemCard(
                            transaction = tx,
                            accountName = accountName,
                            onEdit = { editingTransaction = tx },
                            onDelete = { transactionToDelete = tx },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet for adding expense
    if (showAddExpenseSheet) {
        AddExpenseSheet(
            accounts = uiState.accounts.map { it.account },
            initialAccountId = uiState.selectedAccountId,
            onDismiss = { showAddExpenseSheet = false },
            onSave = { accountId, amount, category, description, date, type ->
                viewModel.addExpense(
                    accountId = accountId,
                    amount = amount,
                    category = category,
                    description = description,
                    date = date,
                    type = type
                )
                showAddExpenseSheet = false
            }
        )
    }

    // Modal Sheet for editing expense
    if (editingTransaction != null) {
        AddExpenseSheet(
            accounts = uiState.accounts.map { it.account },
            initialAccountId = editingTransaction?.accountId,
            transactionToEdit = editingTransaction,
            onDismiss = { editingTransaction = null },
            onSave = { accountId, amount, category, description, date, type ->
                editingTransaction?.let { tx ->
                    viewModel.updateExpense(
                        tx = tx,
                        accountId = accountId,
                        amount = amount,
                        category = category,
                        description = description,
                        date = date,
                        type = type
                    )
                }
                editingTransaction = null
            }
        )
    }

    // Dialog for adding an account
    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, initialBalance, colorHex ->
                viewModel.addAccount(name, initialBalance, colorHex)
                showAddAccountDialog = false
            }
        )
    }

    // Dialog for editing an account
    if (accountToEdit != null) {
        EditAccountDialog(
            account = accountToEdit!!,
            onDismiss = { accountToEdit = null },
            onSave = { name, initialBalance, colorHex ->
                accountToEdit?.let { acc ->
                    viewModel.updateAccount(acc, name, initialBalance, colorHex)
                }
                accountToEdit = null
            },
            onDelete = {
                accountToDelete = accountToEdit
                accountToEdit = null
            }
        )
    }

    // Confirmation dialog for deleting an account
    if (accountToDelete != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            properties = DialogProperties(dismissOnBackPress = false),
            title = { Text("¿Eliminar cuenta?") },
            text = {
                ModalImeBackHandler(onDismiss = { accountToDelete = null })
                Text("Se eliminará la cuenta '${accountToDelete?.name}' y todos sus movimientos asociados.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accountToDelete?.let { viewModel.deleteAccount(it) }
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation dialog for deleting transaction
    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            properties = DialogProperties(dismissOnBackPress = false),
            title = { Text("¿Eliminar movimiento?") },
            text = {
                ModalImeBackHandler(onDismiss = { transactionToDelete = null })
                Text("Se eliminará '${transactionToDelete?.description}' y su saldo se recalculará automáticamente.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionToDelete?.let { viewModel.deleteTransaction(it) }
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun GeneralBalanceCard(
    generalBalance: Double,
    monthlyExpenses: Double,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Balance General Familiar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formatCurrency(generalBalance),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = Color(0xFF9E4726),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Gastos de este mes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatCurrency(monthlyExpenses),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9E4726)
                )
            }
        }
    }
}

@Composable
fun AccountCard(
    accountWithBalance: AccountWithBalance,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val acc = accountWithBalance.account
    val accColor = parseColorSafe(acc.colorHex)

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        label = "accountBorder"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .width(155.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accColor)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar cuenta",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = acc.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatCurrency(accountWithBalance.currentBalance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (accountWithBalance.currentBalance < 0) Color(0xFFBA1A1A) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ExpenseItemCard(
    transaction: FinanceTransactionEntity,
    accountName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpense = transaction.type == "EXPENSE"
    val isPending = transaction.syncStatus == SyncStatus.PENDING_MUTATION

    // Extract emoji from category if available
    val categoryEmoji = when (transaction.category) {
        "Supermercado" -> "🛒"
        "Servicios" -> "💡"
        "Transporte" -> "🚗"
        "Restaurante" -> "🍽️"
        "Salud" -> "💊"
        "Hogar" -> "🏠"
        "Ocio" -> "🍿"
        "Educación" -> "📚"
        else -> "🏷️"
    }

    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = categoryEmoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPending) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(SyncOrange)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Account badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = accountName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${transaction.createdBy}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount & Actions
            Column(horizontalAlignment = Alignment.End) {
                val amountText = if (isExpense) "-${formatCurrency(transaction.amount)}" else "+${formatCurrency(transaction.amount)}"
                val amountColor = if (isExpense) Color(0xFF9E4726) else MaterialTheme.colorScheme.primary

                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFinanceState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AccountBalanceWallet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Sin gastos registrados aún",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Pulsa '+ Nuevo gasto' para anotar un movimiento con su categoría y cuenta.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    accounts: List<AccountEntity>,
    initialAccountId: String?,
    transactionToEdit: FinanceTransactionEntity? = null,
    onDismiss: () -> Unit,
    onSave: (accountId: String, amount: Double, category: String, description: String, date: String, type: String) -> Unit
) {
    val isEditMode = transactionToEdit != null
    var amountText by remember {
        mutableStateOf(
            transactionToEdit?.let {
                if (it.amount % 1.0 == 0.0) it.amount.toLong().toString() else it.amount.toString()
            } ?: ""
        )
    }
    var description by remember { mutableStateOf(transactionToEdit?.description ?: "") }
    var selectedCategory by remember { mutableStateOf(transactionToEdit?.category ?: "Supermercado") }
    var selectedAccountId by remember {
        mutableStateOf(
            transactionToEdit?.accountId ?: initialAccountId ?: accounts.firstOrNull()?.id ?: ""
        )
    }
    var isExpense by remember {
        mutableStateOf(transactionToEdit?.let { it.type != "INCOME" } ?: true)
    }
    var selectedDate by remember {
        mutableStateOf(
            transactionToEdit?.let {
                try {
                    LocalDate.parse(it.date.take(10))
                } catch (e: Exception) {
                    LocalDate.now()
                }
            } ?: LocalDate.now()
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!isEditMode) {
            focusRequester.requestFocus()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        ModalImeBackHandler(onDismiss = onDismiss)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Sheet Header with Expense/Income toggle
            val titleText = when {
                isEditMode && isExpense -> "Editar Gasto"
                isEditMode && !isExpense -> "Editar Ingreso"
                !isEditMode && isExpense -> "Registrar Gasto"
                else -> "Registrar Ingreso"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text("Gasto") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text("Ingreso") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Monto ($)") },
                placeholder = { Text("0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isEditMode) Modifier.focusRequester(focusRequester) else Modifier),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description Input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción / Concepto") },
                placeholder = { Text("ej. Compra supermercado") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Account Selector Chips
            Text(
                text = "Cuenta de cargo:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text(acc.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(parseColorSafe(acc.colorHex))
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Selector Chips
            Text(
                text = "Categoría:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinanceCategories.forEach { (label, catName) ->
                    FilterChip(
                        selected = selectedCategory == catName,
                        onClick = { selectedCategory = catName },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Date Selector with Quick Chips + Full DatePicker
            Text(
                text = "Fecha:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)
                val isOtherDate = selectedDate != today && selectedDate != yesterday

                FilterChip(
                    selected = selectedDate == today,
                    onClick = { selectedDate = today },
                    label = { Text("Hoy") }
                )
                FilterChip(
                    selected = selectedDate == yesterday,
                    onClick = { selectedDate = yesterday },
                    label = { Text("Ayer") }
                )
                FilterChip(
                    selected = isOtherDate,
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            if (isOtherDate) selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            else "Otra fecha"
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    val amountVal = amountText.toDoubleOrNull() ?: 0.0
                    if (amountVal > 0 && selectedAccountId.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val isoDate = selectedDate.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"
                        onSave(
                            selectedAccountId,
                            amountVal,
                            selectedCategory,
                            description,
                            isoDate,
                            if (isExpense) "EXPENSE" else "INCOME"
                        )
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0 && selectedAccountId.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (isEditMode) "Guardar Cambios" else "Guardar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, initialBalance: Double, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#1E523A") }

    val botanicalColors = listOf(
        "#1E523A" to "Verde Bosque",
        "#1976D2" to "Azul Río",
        "#9E4726" to "Terracota",
        "#7A5900" to "Ocre Dorado",
        "#6A1B9A" to "Lavanda"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false),
        title = { Text("Nueva Cuenta") },
        text = {
            ModalImeBackHandler(onDismiss = onDismiss)
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de cuenta") },
                    placeholder = { Text("ej. Tarjeta Débito, Ahorros") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                    label = { Text("Saldo inicial ($)") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Color identificador:",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    botanicalColors.forEach { (hex, _) ->
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseColorSafe(hex))
                                .clickable { selectedColor = hex }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val initBal = initialBalanceText.toDoubleOrNull() ?: 0.0
                        onSave(name.trim(), initBal, selectedColor)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Crear Cuenta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, initialBalance: Double, colorHex: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var initialBalanceText by remember {
        mutableStateOf(
            if (account.initialBalance % 1.0 == 0.0) account.initialBalance.toLong().toString()
            else account.initialBalance.toString()
        )
    }
    var selectedColor by remember { mutableStateOf(account.colorHex) }

    val botanicalColors = listOf(
        "#1E523A" to "Verde Bosque",
        "#1976D2" to "Azul Río",
        "#9E4726" to "Terracota",
        "#7A5900" to "Ocre Dorado",
        "#6A1B9A" to "Lavanda"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false),
        title = { Text("Editar Cuenta") },
        text = {
            ModalImeBackHandler(onDismiss = onDismiss)
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de cuenta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                    label = { Text("Saldo inicial ($)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Color identificador:",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    botanicalColors.forEach { (hex, _) ->
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseColorSafe(hex))
                                .clickable { selectedColor = hex }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminar esta cuenta")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val initBal = initialBalanceText.toDoubleOrNull() ?: 0.0
                        onSave(name.trim(), initBal, selectedColor)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Guardar Cambios")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
