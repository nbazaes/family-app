package com.familyapp.ui.calendar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyapp.core.database.entity.CalendarEventEntity
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.ui.theme.SyncOrange
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
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddSheet by remember { mutableStateOf(false) }
    var showFeedDialog by remember { mutableStateOf(false) }

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
                text = { Text("Nuevo evento", fontWeight = FontWeight.SemiBold) },
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
            // 1. Member filter + iCal action bar
            CalendarFilterAndActionsBar(
                members = uiState.members,
                selectedFilter = uiState.selectedMemberFilter,
                currentUserName = uiState.currentUserName,
                onFilterSelected = { filter ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.selectMemberFilter(filter)
                },
                onOpenFeed = { showFeedDialog = true }
            )

            // 2. Interactive Week Strip
            WeekStrip(
                selectedDate = uiState.selectedDate,
                datesWithEvents = uiState.datesWithEvents,
                onDateSelected = { date ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.selectDate(date)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Events Content
            val todayStr = LocalDate.now().toString()
            val selectedDateStr = uiState.selectedDate.toString()
            val isSelectedDateToday = selectedDateStr == todayStr

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header for Selected Date
                item(key = "selected_date_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatFullDateHeader(uiState.selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelectedDateToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelectedDateToday) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Hoy",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Events on Selected Date
                if (uiState.selectedDateEvents.isEmpty()) {
                    item(key = "empty_selected_date") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "Sin eventos programados para este día",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { showAddSheet = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Agregar evento aquí")
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.selectedDateEvents, key = { it.id }) { event ->
                        SwipeableCalendarEventCard(
                            event = event,
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteEvent(event)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Evento eliminado",
                                        actionLabel = "Deshacer",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreEvent(event)
                                    }
                                }
                            }
                        )
                    }
                }

                // Upcoming events section (after selected date)
                if (uiState.otherUpcomingEvents.isNotEmpty()) {
                    item(key = "upcoming_header") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "PRÓXIMOS DÍAS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    uiState.otherUpcomingEvents.forEach { (dateStr, events) ->
                        item(key = "upcoming_group_$dateStr") {
                            Text(
                                text = formatShortDateHeader(dateStr),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                            )
                        }

                        items(events, key = { it.id }) { event ->
                            SwipeableCalendarEventCard(
                                event = event,
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteEvent(event)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Evento eliminado",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreEvent(event)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Add Event Bottom Sheet
        if (showAddSheet) {
            AddEventBottomSheet(
                initialDate = uiState.selectedDate,
                members = uiState.members,
                currentUserName = uiState.currentUserName,
                onDismiss = { showAddSheet = false },
                onConfirm = { title, desc, date, start, end, isAllDay, colorHex, createdBy ->
                    viewModel.addEvent(title, desc, date, start, end, isAllDay, colorHex, createdBy)
                    showAddSheet = false
                }
            )
        }

        // iCal Feed Dialog
        if (showFeedDialog) {
            AlertDialog(
                onDismissRequest = { showFeedDialog = false },
                icon = { Icon(Icons.Default.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Suscripción de Calendario (.ics)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Sincroniza este calendario con Google Calendar, Apple Calendar o Thunderbird con este enlace dinámico:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = uiState.feedUrl,
                            onValueChange = {},
                            readOnly = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("FamilyApp iCal Feed", uiState.feedUrl))
                            Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            showFeedDialog = false
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Enlace")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFeedDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarFilterAndActionsBar(
    members: List<String>,
    selectedFilter: String,
    currentUserName: String?,
    onFilterSelected: (String) -> Unit,
    onOpenFeed: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { onFilterSelected("ALL") },
                    label = { Text("Todos") }
                )
            }

            if (!currentUserName.isNullOrBlank()) {
                item {
                    FilterChip(
                        selected = selectedFilter == "MINE",
                        onClick = { onFilterSelected("MINE") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Mis eventos") }
                    )
                }
            }

            items(members.filter { it != currentUserName }) { member ->
                FilterChip(
                    selected = selectedFilter == member,
                    onClick = { onFilterSelected(member) },
                    label = { Text(member) }
                )
            }
        }

        IconButton(
            onClick = onOpenFeed,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.RssFeed,
                contentDescription = "Suscripción iCal",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun WeekStrip(
    selectedDate: LocalDate,
    datesWithEvents: Set<String>,
    onDateSelected: (LocalDate) -> Unit
) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val today = remember { LocalDate.now() }

    // Base start of week (Monday)
    val currentWeekMonday = remember(weekOffset) {
        today.plusWeeks(weekOffset.toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    val daysOfWeek = remember(currentWeekMonday) {
        (0L..6L).map { currentWeekMonday.plusDays(it) }
    }

    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es")) }
    val monthTitle = currentWeekMonday.format(monthFormatter).replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Week controls header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (weekOffset != 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            weekOffset = 0
                            onDateSelected(today)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hoy", fontSize = 12.sp)
                    }
                }
            }

            Row {
                IconButton(
                    onClick = { weekOffset -= 1 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Semana anterior")
                }
                IconButton(
                    onClick = { weekOffset += 1 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Semana siguiente")
                }
            }
        }

        // Days cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val dayNameFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale("es")) }

            daysOfWeek.forEach { date ->
                val isSelected = date.isEqual(selectedDate)
                val isToday = date.isEqual(today)
                val hasEvents = datesWithEvents.contains(date.toString())

                DayCard(
                    date = date,
                    dayName = date.format(dayNameFormatter).take(3).replaceFirstChar { it.uppercase() },
                    isSelected = isSelected,
                    isToday = isToday,
                    hasEvents = hasEvents,
                    onClick = { onDateSelected(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DayCard(
    date: LocalDate,
    dayName: String,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "dayCardContainer"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "dayCardContent"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) contentColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Event indicator dot
            if (hasEvents) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary)
                )
            } else {
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCalendarEventCard(
    event: CalendarEventEntity,
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
        CalendarEventCard(event = event, onDelete = onDelete)
    }
}

@Composable
fun CalendarEventCard(
    event: CalendarEventEntity,
    onDelete: () -> Unit
) {
    val eventColor = try {
        Color(android.graphics.Color.parseColor(event.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left organic color badge / pill
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(eventColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!event.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Capsule
                    val timeLabel = if (event.isAllDay) {
                        "Todo el día"
                    } else {
                        val start = event.startTime.drop(11).take(5)
                        val end = event.endTime.drop(11).take(5)
                        if (start.isNotBlank() && end.isNotBlank()) "$start - $end" else "Horario fijado"
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Creator Avatar
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = event.createdBy.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = event.createdBy,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Sync Status
            if (event.syncStatus == SyncStatus.PENDING_MUTATION) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SyncOrange)
                )
            }

            // Delete Action
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar evento",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventBottomSheet(
    initialDate: LocalDate,
    members: List<String>,
    currentUserName: String?,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String?,
        date: String,
        startTime: String,
        endTime: String,
        isAllDay: Boolean,
        colorHex: String,
        createdBy: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    var isAllDay by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("10:00") }
    var endTime by remember { mutableStateOf("11:30") }

    var createdBy by remember { mutableStateOf(currentUserName ?: "Yo") }
    var selectedColorHex by remember { mutableStateOf("#1E523A") } // Default Botanical Forest Green

    // Botanical palette choices
    val botanicalColors = listOf(
        "#1E523A", // Forest Green
        "#9E4726", // Terracotta
        "#7A5900", // Golden Ochre
        "#388E3C", // Sage Green
        "#1976D2", // River Blue
        "#8E24AA"  // Mulberry
    )

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
                text = "Nuevo evento familiar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título del evento") },
                placeholder = { Text("Ej: Consulta médica, Reunión escolar, Cumpleaños...") },
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

            // Description / Location
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Lugar o detalles (opcional)") },
                placeholder = { Text("Ej: Clínica Alemana, Casa de los abuelos...") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Date Selection with presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Fecha:",
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
                    val today = LocalDate.now()
                    val tomorrow = today.plusDays(1)
                    val saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

                    FilterChip(
                        selected = selectedDate.isEqual(today),
                        onClick = { selectedDate = today },
                        label = { Text("Hoy") }
                    )
                    FilterChip(
                        selected = selectedDate.isEqual(tomorrow),
                        onClick = { selectedDate = tomorrow },
                        label = { Text("Mañana") }
                    )
                    FilterChip(
                        selected = selectedDate.isEqual(saturday),
                        onClick = { selectedDate = saturday },
                        label = { Text("Fin de semana") }
                    )

                    AssistChip(
                        onClick = { showDatePicker = true },
                        leadingIcon = {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(formatDisplayDate(selectedDate.toString()))
                        }
                    )
                }
            }

            // Time presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Horario:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isAllDay = !isAllDay }
                    ) {
                        Checkbox(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            modifier = Modifier.size(36.dp)
                        )
                        Text("Todo el día", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!isAllDay) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Presets
                        FilterChip(
                            selected = startTime == "09:00" && endTime == "10:30",
                            onClick = {
                                startTime = "09:00"
                                endTime = "10:30"
                            },
                            label = { Text("Mañana (09:00)") }
                        )
                        FilterChip(
                            selected = startTime == "14:00" && endTime == "15:30",
                            onClick = {
                                startTime = "14:00"
                                endTime = "15:30"
                            },
                            label = { Text("Tarde (14:00)") }
                        )
                        FilterChip(
                            selected = startTime == "19:00" && endTime == "21:00",
                            onClick = {
                                startTime = "19:00"
                                endTime = "21:00"
                            },
                            label = { Text("Noche (19:00)") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("Inicio (HH:MM)") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("Fin (HH:MM)") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Color Selector (Botanical Swatches)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Color del evento:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    botanicalColors.forEach { hex ->
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        val color = Color(android.graphics.Color.parseColor(hex))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColorHex = hex }
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Responsible / Creator
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Creado por:",
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
                        val isSelected = createdBy.equals(member, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { createdBy = member },
                            label = { Text(if (member == currentUserName) "Yo ($member)" else member) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title,
                            description.ifBlank { null },
                            selectedDate.toString(),
                            startTime,
                            endTime,
                            isAllDay,
                            selectedColorHex,
                            createdBy
                        )
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
                Text("Guardar Evento", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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

// Helpers
fun formatFullDateHeader(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))
    return date.format(formatter).replaceFirstChar { it.uppercase() }
}

fun formatShortDateHeader(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr.take(10))
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        when {
            date.isEqual(today) -> "Hoy, ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))}"
            date.isEqual(tomorrow) -> "Mañana, ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))}"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))
                date.format(formatter).replaceFirstChar { it.uppercase() }
            }
        }
    } catch (e: Exception) {
        dateStr
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

