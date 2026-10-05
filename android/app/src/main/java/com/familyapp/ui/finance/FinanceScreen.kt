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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import java.time.LocalDate
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
    var showAddAccountDialog by remember { mutableStateOf(false) }
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

    // Confirmation dialog for deleting transaction
    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("¿Eliminar movimiento?") },
            text = { Text("Se eliminará '${transactionToDelete?.description}' y su saldo se recalculará automáticamente.") },
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
    onClick: () -> Unit
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
            .width(150.dp)
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
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accColor)
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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

            // Amount & Delete action
            Column(horizontalAlignment = Alignment.End) {
                val amountText = if (isExpense) "-${formatCurrency(transaction.amount)}" else "+${formatCurrency(transaction.amount)}"
                val amountColor = if (isExpense) Color(0xFF9E4726) else MaterialTheme.colorScheme.primary

                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
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
    onDismiss: () -> Unit,
    onSave: (accountId: String, amount: Double, category: String, description: String, date: String, type: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Supermercado") }
    var selectedAccountId by remember { mutableStateOf(initialAccountId ?: accounts.firstOrNull()?.id ?: "") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpense) "Registrar Gasto" else "Registrar Ingreso",
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
                    .focusRequester(focusRequester),
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

            // Date Quick Selector
            Text(
                text = "Fecha:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)

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
                Text("Guardar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
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
                    onValueChange = { initialBalanceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
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
                        val isSelected = selectedColor == hex
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
