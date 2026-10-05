package com.familyapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.familyapp.core.database.FamilyDatabase
import com.familyapp.core.network.NetworkClient
import com.familyapp.core.network.dto.LoginRequestDto
import com.familyapp.core.network.dto.RegisterRequestDto
import com.familyapp.data.repository.CalendarRepository
import com.familyapp.data.repository.ShoppingRepository
import com.familyapp.data.repository.TasksRepository
import com.familyapp.sync.SyncManager
import com.familyapp.ui.calendar.CalendarScreen
import com.familyapp.ui.calendar.CalendarViewModel
import com.familyapp.ui.components.SyncBadge
import com.familyapp.ui.navigation.FamilyFloatingBottomBar
import com.familyapp.ui.navigation.Screen
import com.familyapp.ui.shopping.ShoppingScreen
import com.familyapp.ui.shopping.ShoppingViewModel
import com.familyapp.ui.tasks.TasksScreen
import com.familyapp.ui.tasks.TasksViewModel
import com.familyapp.ui.theme.FamilyAppTheme
import com.familyapp.sync.SyncUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {

    private lateinit var syncManager: SyncManager
    private lateinit var shoppingViewModel: ShoppingViewModel
    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var calendarViewModel: CalendarViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = FamilyDatabase.getInstance(applicationContext)
        syncManager = SyncManager.getInstance(applicationContext)

        val shoppingRepo = ShoppingRepository(db.itemDao(), syncManager)
        val tasksRepo = TasksRepository(db.itemDao(), syncManager)
        val calendarRepo = CalendarRepository(db.calendarEventDao(), syncManager)

        shoppingViewModel = ShoppingViewModel(shoppingRepo)
        tasksViewModel = TasksViewModel(tasksRepo)
        calendarViewModel = CalendarViewModel(calendarRepo)

        setContent {
            FamilyAppTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Shopping.route
                val syncState by syncManager.syncUiState.collectAsState()

                var showServerConfigDialog by remember {
                    // Auto-open settings dialog on first launch if not logged in
                    mutableStateOf(!NetworkClient.isLoggedIn())
                }
                var authStateVersion by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        FamilyTopHeader(
                            syncState = syncState,
                            isLoggedIn = NetworkClient.isLoggedIn(),
                            userName = NetworkClient.currentUserName,
                            onSyncClick = { syncManager.scheduleSync() },
                            onSettingsClick = { showServerConfigDialog = true }
                        )
                    },
                    bottomBar = {
                        FamilyFloatingBottomBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(Screen.Shopping.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                ) { innerPadding ->

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Shopping.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Shopping.route) {
                            ShoppingScreen(viewModel = shoppingViewModel)
                        }
                        composable(Screen.Tasks.route) {
                            TasksScreen(viewModel = tasksViewModel)
                        }
                        composable(Screen.Calendar.route) {
                            CalendarScreen(viewModel = calendarViewModel)
                        }
                    }

                    if (showServerConfigDialog) {
                        ServerConfigDialog(
                            onDismiss = { showServerConfigDialog = false },
                            onAuthChanged = {
                                authStateVersion++
                                syncManager.restartRealtimeSync()
                                syncManager.scheduleSync()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (NetworkClient.isLoggedIn()) {
            syncManager.startRealtimeSync()
            syncManager.scheduleSync()
        }
    }

    override fun onPause() {
        super.onPause()
        syncManager.stopRealtimeSync()
    }
}

@Composable
fun ServerConfigDialog(
    onDismiss: () -> Unit,
    onAuthChanged: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf(NetworkClient.baseUrl) }
    var memberName by remember { mutableStateOf(NetworkClient.currentUserName ?: "Familiar") }
    var familyCode by remember { mutableStateOf("") }

    var showAdvancedEmailAuth by remember { mutableStateOf(false) }
    var emailLoginEmail by remember { mutableStateOf("") }
    var emailLoginPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val isLoggedIn = NetworkClient.isLoggedIn()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLoggedIn) "Estado de la Conexión" else "Conectar con tu Familia") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Server URL field
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        NetworkClient.updateBaseUrl(it)
                    },
                    label = { Text("URL del Servidor") },
                    placeholder = { Text("http://192.168.1.100:8000/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLoggedIn) {
                    // Logged in state
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "✓ Conectado a la Familia",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Miembro: ${NetworkClient.currentUserName ?: "Familiar"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Familia ID: ${NetworkClient.currentFamilyId.take(8)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onAuthChanged()
                                Toast.makeText(context, "Sincronizando...", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sincronizar")
                        }

                        OutlinedButton(
                            onClick = {
                                NetworkClient.clearAuth()
                                onAuthChanged()
                                Toast.makeText(context, "Desconectado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Desconectar")
                        }
                    }
                } else {
                    // Zero-account Instant Connect (Default & Recommended)
                    if (!showAdvancedEmailAuth) {
                        OutlinedTextField(
                            value = memberName,
                            onValueChange = { memberName = it },
                            label = { Text("Tu Nombre (ej: Papá, Nicolás)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = familyCode,
                            onValueChange = { familyCode = it },
                            label = { Text("Código de Familia (opcional)") },
                            placeholder = { Text("Dejar vacío para familia default") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                isLoading = true
                                statusMessage = null
                                isError = false
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.updateBaseUrl(urlText)
                                        val req = com.familyapp.core.network.dto.ConnectRequestDto(
                                            memberName = memberName.trim().ifBlank { "Familiar" },
                                            familyCode = familyCode.trim().ifBlank { null }
                                        )
                                        val resp = withContext(Dispatchers.IO) {
                                            NetworkClient.apiService.connectFamily(req)
                                        }
                                        if (resp.isSuccessful && resp.body() != null) {
                                            val body = resp.body()!!
                                            NetworkClient.setAuth(
                                                token = body.accessToken,
                                                familyId = body.familyId,
                                                name = body.name,
                                                email = body.email ?: ""
                                            )
                                            onAuthChanged()
                                            Toast.makeText(context, "¡Conectado como ${body.name}!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            isError = true
                                            statusMessage = "Error ${resp.code()}: ${resp.errorBody()?.string()}"
                                        }
                                    } catch (e: Exception) {
                                        isError = true
                                        statusMessage = "No se pudo conectar al servidor: ${e.localizedMessage ?: e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && memberName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Conectar y Sincronizar")
                            }
                        }

                        TextButton(
                            onClick = { showAdvancedEmailAuth = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("¿Prefieres usar correo y contraseña?", fontSize = 12.sp)
                        }
                    } else {
                        // Email/password option
                        OutlinedTextField(
                            value = emailLoginEmail,
                            onValueChange = { emailLoginEmail = it },
                            label = { Text("Correo electrónico") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = emailLoginPassword,
                            onValueChange = { emailLoginPassword = it },
                            label = { Text("Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                isLoading = true
                                statusMessage = null
                                isError = false
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.updateBaseUrl(urlText)
                                        val resp = withContext(Dispatchers.IO) {
                                            NetworkClient.apiService.login(
                                                com.familyapp.core.network.dto.LoginRequestDto(
                                                    email = emailLoginEmail.trim(),
                                                    password = emailLoginPassword
                                                )
                                            )
                                        }
                                        if (resp.isSuccessful && resp.body() != null) {
                                            val body = resp.body()!!
                                            NetworkClient.setAuth(
                                                token = body.accessToken,
                                                familyId = body.familyId,
                                                name = body.name,
                                                email = body.email ?: ""
                                            )
                                            onAuthChanged()
                                            Toast.makeText(context, "¡Conectado como ${body.name}!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            isError = true
                                            statusMessage = "Error ${resp.code()}: Credenciales incorrectas"
                                        }
                                    } catch (e: Exception) {
                                        isError = true
                                        statusMessage = "Error de conexión: ${e.localizedMessage ?: e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && emailLoginEmail.isNotBlank() && emailLoginPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Entrar con Cuenta")
                        }

                        TextButton(
                            onClick = { showAdvancedEmailAuth = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Volver a conexión rápida sin cuenta", fontSize = 12.sp)
                        }
                    }
                }

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoggedIn) {
                TextButton(onClick = onDismiss) {
                    Text("Continuar Offline")
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun FamilyTopHeader(
    syncState: SyncUiState,
    isLoggedIn: Boolean,
    userName: String?,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es")) }
    val formattedDate = remember(today) { today.format(dateFormatter).replaceFirstChar { it.uppercase() } }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🌿", fontSize = 18.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "FAMILYAPP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = if (!userName.isNullOrBlank()) "Hogar de $userName" else "Hogar Conectado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoggedIn) {
                        SyncBadge(
                            state = syncState,
                            onSyncClick = onSyncClick
                        )
                    } else {
                        AssistChip(
                            onClick = onSettingsClick,
                            label = { Text("Conectar", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Configuración",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.3).sp
            )
        }
    }
}

