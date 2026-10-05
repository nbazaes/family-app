package com.familyapp.ui.shopping

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyapp.core.database.entity.ItemEntity
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.ui.components.ModalImeBackHandler
import com.familyapp.ui.theme.SyncOrange
import kotlinx.coroutines.launch

// Common household staple presets for zero-friction one-tap addition
val HouseholdStaples = listOf(
    "🥛 Leche" to "Lácteos",
    "🥚 Huevos" to "Despensa",
    "🍞 Pan" to "Panadería",
    "☕ Café" to "Despensa",
    "🍎 Manzanas" to "Frutas y Verduras",
    "🧀 Queso" to "Lácteos",
    "🍚 Arroz" to "Despensa",
    "🧻 Papel Higiénico" to "Limpieza"
)

val ShoppingCategories = listOf(
    "🥦 Frutas y Verduras",
    "🧀 Lácteos",
    "🍞 Panadería",
    "🥫 Despensa",
    "🥩 Carnes",
    "🧼 Limpieza",
    "📦 General"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddSheet = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo producto", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick Staples Strip (One-tap addition of common pantry items)
            QuickStaplesBar(
                onStapleClick = { nameWithEmoji, cat ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val cleanName = nameWithEmoji.substringAfter(" ").trim()
                    viewModel.addItem(cleanName, "1", cat)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "$cleanName agregado a la lista",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )

            val totalItems = uiState.totalPending + uiState.totalCompleted

            if (uiState.itemsByCategory.isEmpty() && !uiState.isLoading) {
                ShoppingEmptyState(onAddClick = { showAddSheet = true })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary status card
                    if (totalItems > 0) {
                        item(key = "shopping_summary") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.LocalMall,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "${uiState.totalPending} productos por comprar",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (uiState.totalCompleted > 0) {
                                                Text(
                                                    text = "${uiState.totalCompleted} en el carrito",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }

                                    // Completion ratio pill
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "${uiState.totalCompleted}/$totalItems",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Categorized Items
                    uiState.itemsByCategory.forEach { (category, items) ->
                        item(key = "cat_header_$category") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${items.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        items(items, key = { it.id }) { item ->
                            SwipeableShoppingItemRow(
                                item = item,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleItem(item)
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteItem(item)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "${item.title} eliminado",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreItem(item)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            AddShoppingItemBottomSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { title, qty, cat ->
                    viewModel.addItem(title, qty, cat)
                    showAddSheet = false
                }
            )
        }
    }
}

@Composable
fun QuickStaplesBar(
    onStapleClick: (nameWithEmoji: String, cat: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HouseholdStaples.forEach { (staple, cat) ->
                Surface(
                    onClick = { onStapleClick(staple, cat) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = staple,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableShoppingItemRow(
    item: ItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Eliminar",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        ShoppingItemCard(item = item, onToggle = onToggle, onDelete = onDelete)
    }
}

@Composable
fun ShoppingItemCard(
    item: ItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = if (item.isCompleted)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isCompleted) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        tonalElevation = if (item.isCompleted) 0.dp else 1.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!item.quantity.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Cant: ${item.quantity}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Sync Status Dot
            if (item.syncStatus == SyncStatus.PENDING_MUTATION) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SyncOrange)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShoppingItemBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (title: String, quantity: String?, category: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    var title by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ShoppingCategories.first()) }

    val quantityPresets = listOf("1", "2", "3", "500g", "1kg", "1L", "Pack")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        ModalImeBackHandler(onDismiss = onDismiss)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Agregar a la compra",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Producto") },
                placeholder = { Text("Ej: Palta, Tomates, Aceite de oliva...") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            // Quantity Presets & Input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Cantidad:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quantityPresets.forEach { preset ->
                        FilterChip(
                            selected = quantity == preset,
                            onClick = { quantity = if (quantity == preset) "" else preset },
                            label = { Text(preset) }
                        )
                    }
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Cantidad personalizada (opcional)") },
                    placeholder = { Text("Ej: 2 unidades, 500g") },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Categoría:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShoppingCategories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Confirm Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val cleanCat = selectedCategory.substringAfter(" ").trim()
                        onConfirm(title, quantity.ifBlank { null }, cleanCat)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar en la lista", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ShoppingEmptyState(
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "La despensa está lista",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "No hay productos pendientes de compra. Agrega artículos tocando un elemento rápido arriba o con el botón inferior.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agregar producto")
            }
        }
    }
}
