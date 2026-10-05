package com.familyapp.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.familyapp.ui.components.ModalImeBackHandler
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
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddSheet = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva tarea", fontWeight = FontWeight.SemiBold) },
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
            // Filter Bar
            MemberFilterBar(
                members = uiState.members,
                selectedFilter = uiState.selectedFilter,
                pendingCountAll = uiState.pendingCountAll,
                pendingCountMine = uiState.pendingCountMine,
                memberCounts = uiState.memberPendingCounts,
                currentUserName = uiState.currentUserName,
                onFilterSelected = { filter ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setFilter(filter)
                }
            )

            val pendingGroups = uiState.pendingGroups
            val hasPending = pendingGroups.totalCount > 0
            val hasCompleted = uiState.completedTasks.isNotEmpty()

            if (!hasPending && !hasCompleted && !uiState.isLoading) {
                TasksEmptyState(
                    selectedFilter = uiState.selectedFilter,
                    currentUserName = uiState.currentUserName,
                    onAddTask = { showAddSheet = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. OVERDUE
                    if (pendingGroups.overdue.isNotEmpty()) {
                        item(key = "header_overdue") {
                            SectionHeader(
                                title = "VENCIDAS",
                                count = pendingGroups.overdue.size,
                                color = MaterialTheme.colorScheme.error,
                                icon = Icons.Default.WarningAmber
                            )
                        }
                        items(pendingGroups.overdue, key = { it.id }) { task ->
                            SwipeableTaskRow(
                                task = task,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea marcada como completada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.toggleTask(task)
                                        }
                                    }
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea eliminada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreTask(task)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 2. TODAY
                    if (pendingGroups.today.isNotEmpty()) {
                        item(key = "header_today") {
                            SectionHeader(
                                title = "HOY",
                                count = pendingGroups.today.size,
                                color = MaterialTheme.colorScheme.primary,
                                icon = Icons.Default.CalendarToday
                            )
                        }
                        items(pendingGroups.today, key = { it.id }) { task ->
                            SwipeableTaskRow(
                                task = task,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea marcada como completada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.toggleTask(task)
                                        }
                                    }
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea eliminada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreTask(task)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 3. UPCOMING
                    if (pendingGroups.upcoming.isNotEmpty()) {
                        item(key = "header_upcoming") {
                            SectionHeader(
                                title = "PRÓXIMAS",
                                count = pendingGroups.upcoming.size,
                                color = MaterialTheme.colorScheme.secondary,
                                icon = Icons.Default.CalendarMonth
                            )
                        }
                        items(pendingGroups.upcoming, key = { it.id }) { task ->
                            SwipeableTaskRow(
                                task = task,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea marcada como completada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.toggleTask(task)
                                        }
                                    }
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea eliminada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreTask(task)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 4. NO DATE
                    if (pendingGroups.noDate.isNotEmpty()) {
                        item(key = "header_nodate") {
                            SectionHeader(
                                title = "SIN FECHA PROGRAMADA",
                                count = pendingGroups.noDate.size,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(pendingGroups.noDate, key = { it.id }) { task ->
                            SwipeableTaskRow(
                                task = task,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea marcada como completada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.toggleTask(task)
                                        }
                                    }
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteTask(task)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Tarea eliminada",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreTask(task)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 5. COMPLETED SECTION (ACCORDION)
                    if (hasCompleted) {
                        item(key = "header_completed") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { completedExpanded = !completedExpanded }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Completadas (${uiState.completedTasks.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = if (completedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (completedExpanded) "Ocultar" else "Mostrar",
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        if (completedExpanded) {
                            items(uiState.completedTasks, key = { it.id }) { task ->
                                SwipeableTaskRow(
                                    task = task,
                                    onToggle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.toggleTask(task)
                                    },
                                    onDelete = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.deleteTask(task)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            AddTaskBottomSheet(
                members = uiState.members,
                currentUserName = uiState.currentUserName,
                onDismiss = { showAddSheet = false },
                onConfirm = { title, assignedTo, dueDate ->
                    viewModel.addTask(title, assignedTo, dueDate)
                    showAddSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberFilterBar(
    members: List<String>,
    selectedFilter: String,
    pendingCountAll: Int,
    pendingCountMine: Int,
    memberCounts: Map<String, Int>,
    currentUserName: String?,
    onFilterSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "Todas"
        item {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { onFilterSelected("ALL") },
                label = {
                    Text(
                        text = if (pendingCountAll > 0) "Todas ($pendingCountAll)" else "Todas",
                        fontWeight = if (selectedFilter == "ALL") FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }

        // "Mis tareas"
        if (!currentUserName.isNullOrBlank()) {
            item {
                FilterChip(
                    selected = selectedFilter == "MINE",
                    onClick = { onFilterSelected("MINE") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (pendingCountMine > 0) "Mis tareas ($pendingCountMine)" else "Mis tareas",
                            fontWeight = if (selectedFilter == "MINE") FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        // Dynamic Members
        items(members.filter { it != currentUserName }) { member ->
            val count = memberCounts[member] ?: 0
            FilterChip(
                selected = selectedFilter == member,
                onClick = { onFilterSelected(member) },
                label = {
                    Text(
                        text = if (count > 0) "$member ($count)" else member,
                        fontWeight = if (selectedFilter == member) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskRow(
    task: ItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggle()
                    false // Return false so item animates back rather than disappearing immediately without confirmation
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Completar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (task.isCompleted) "Reactivar" else "Completar",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
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
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        TaskCard(
            task = task,
            onToggle = onToggle,
            onDelete = onDelete
        )
    }
}

@Composable
fun TaskCard(
    task: ItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(48.dp) // Accessible 48dp touch target
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (!task.assignedTo.isNullOrBlank() || !task.dueDate.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Assignee chip
                        if (!task.assignedTo.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = task.assignedTo.take(1).uppercase(),
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = task.assignedTo,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // Due date chip
                        if (!task.dueDate.isNullOrBlank()) {
                            val (dateLabel, isOverdue, isToday) = parseDateStatus(task.dueDate)
                            val containerColor = when {
                                task.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                                isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                            val textColor = when {
                                task.isCompleted -> MaterialTheme.colorScheme.outline
                                isOverdue -> MaterialTheme.colorScheme.error
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = containerColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOverdue && !task.isCompleted) Icons.Default.WarningAmber else Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = textColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sync Indicator
            if (task.syncStatus == SyncStatus.PENDING_MUTATION) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF9800))
                )
            }

            // Delete button (48dp target)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar tarea",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskBottomSheet(
    members: List<String>,
    currentUserName: String?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, assignedTo: String?, dueDate: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    var title by remember { mutableStateOf("") }
    var assignedTo by remember { mutableStateOf(currentUserName ?: "") }
    var dueDate by remember { mutableStateOf<String?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Nueva tarea familiar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Title field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("¿Qué hay que hacer?") },
                placeholder = { Text("Ej: Comprar leche, pagar cuentas, sacar basura...") },
                shape = RoundedCornerShape(12.dp),
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (title.isNotBlank()) {
                        onConfirm(title, assignedTo.ifBlank { null }, dueDate)
                    }
                })
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            // Assignee chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Asignar a:",
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
                    val allMembersToDisplay = buildList {
                        if (!currentUserName.isNullOrBlank()) add(currentUserName)
                        members.forEach { if (!contains(it)) add(it) }
                    }

                    allMembersToDisplay.forEach { member ->
                        val isSelected = assignedTo.equals(member, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                assignedTo = if (isSelected) "" else member
                            },
                            label = { Text(if (member == currentUserName) "Yo ($member)" else member) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Due Date presets & custom picker
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Fecha límite:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val todayStr = LocalDate.now().toString()
                    val tomorrowStr = LocalDate.now().plusDays(1).toString()
                    val weekendStr = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toString()

                    // "Hoy" preset
                    FilterChip(
                        selected = dueDate == todayStr,
                        onClick = {
                            dueDate = if (dueDate == todayStr) null else todayStr
                        },
                        label = { Text("Hoy") }
                    )

                    // "Mañana" preset
                    FilterChip(
                        selected = dueDate == tomorrowStr,
                        onClick = {
                            dueDate = if (dueDate == tomorrowStr) null else tomorrowStr
                        },
                        label = { Text("Mañana") }
                    )

                    // "Fin de semana" preset
                    FilterChip(
                        selected = dueDate == weekendStr,
                        onClick = {
                            dueDate = if (dueDate == weekendStr) null else weekendStr
                        },
                        label = { Text("Fin de semana") }
                    )

                    // Date picker button
                    AssistChip(
                        onClick = { showDatePickerDialog = true },
                        leadingIcon = {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                if (dueDate != null && dueDate != todayStr && dueDate != tomorrowStr && dueDate != weekendStr)
                                    formatDisplayDate(dueDate!!)
                                else "Elegir fecha"
                            )
                        }
                    )

                    // Clear date chip
                    if (dueDate != null) {
                        IconButton(
                            onClick = { dueDate = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Quitar fecha",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, assignedTo.ifBlank { null }, dueDate)
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
                Text("Guardar tarea", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val selectedLocalDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            dueDate = selectedLocalDate.toString()
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun TasksEmptyState(
    selectedFilter: String,
    currentUserName: String?,
    onAddTask: () -> Unit
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (selectedFilter == "MINE") "¡Estás al día!" else "No hay tareas pendientes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (selectedFilter) {
                        "MINE" -> "No tienes ninguna tarea asignada por ahora."
                        "ALL" -> "El hogar está completamente al día. Puedes crear una nueva tarea con el botón inferior."
                        else -> "No hay tareas asignadas a $selectedFilter actualmente."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Button(
                onClick = onAddTask,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear nueva tarea")
            }
        }
    }
}

// Helpers
fun parseDateStatus(dueStr: String): Triple<String, Boolean, Boolean> {
    return try {
        val date = LocalDate.parse(dueStr.take(10))
        val today = LocalDate.now()
        val isOverdue = date.isBefore(today)
        val isToday = date.isEqual(today)
        val isTomorrow = date.isEqual(today.plusDays(1))

        val label = when {
            isToday -> "Hoy"
            isTomorrow -> "Mañana"
            isOverdue -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
                if (days == 1L) "Ayer" else "Atrasada ($days d)"
            }
            else -> {
                val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))
                date.format(formatter)
            }
        }
        Triple(label, isOverdue, isToday)
    } catch (e: Exception) {
        Triple(dueStr.take(10), false, false)
    }
}

fun formatDisplayDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr.take(10))
        val formatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale("es"))
        date.format(formatter).replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
        dateStr
    }
}
