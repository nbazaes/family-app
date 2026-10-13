package com.familyapp.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.familyapp.core.database.FamilyDatabase
import com.familyapp.core.database.entity.SyncStatus
import com.familyapp.core.network.NetworkClient
import com.familyapp.core.network.dto.AccountDto
import com.familyapp.core.network.dto.BatchSyncEventsRequestDto
import com.familyapp.core.network.dto.BatchSyncFinanceRequestDto
import com.familyapp.core.network.dto.BatchSyncItemsRequestDto
import com.familyapp.core.network.dto.CalendarEventDto
import com.familyapp.core.network.dto.FinanceTransactionDto
import com.familyapp.core.network.dto.ItemDto
import com.familyapp.core.network.sse.SseClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class SyncUiState {
    object IDLE : SyncUiState()
    object SYNCING : SyncUiState()
    object OFFLINE : SyncUiState()
    data class ERROR(
        val title: String,
        val message: String,
        val solution: String,
        val failedModule: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncUiState()
}

data class SyncErrorDetails(
    val title: String,
    val message: String,
    val solution: String,
    val failedModule: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class SyncManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val sseClient = SseClient()
    private val db = FamilyDatabase.getInstance(context)
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val syncMutex = Mutex()

    private val _syncUiState = MutableStateFlow<SyncUiState>(SyncUiState.IDLE)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    private val _currentError = MutableStateFlow<SyncErrorDetails?>(null)
    val currentError: StateFlow<SyncErrorDetails?> = _currentError.asStateFlow()

    private var sseJob: Job? = null

    init {
        // Observe WorkManager batch sync status to reflect background progress in the UI
        coroutineScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("FamilyBatchSync")
                .collect { workInfos ->
                    val activeWork = workInfos.firstOrNull()
                    if (activeWork != null) {
                        when (activeWork.state) {
                            WorkInfo.State.RUNNING -> {
                                if (_currentError.value == null) {
                                    Log.d("SyncManager", "WorkManager is RUNNING -> SYNCING")
                                    _syncUiState.value = SyncUiState.SYNCING
                                }
                            }
                            WorkInfo.State.ENQUEUED -> {
                                // Do NOT overwrite ERROR state or lock into SYNCING while waiting in queue
                                val err = _currentError.value
                                if (err != null) {
                                    _syncUiState.value = SyncUiState.ERROR(
                                        title = err.title,
                                        message = err.message,
                                        solution = err.solution,
                                        failedModule = err.failedModule,
                                        timestamp = err.timestamp
                                    )
                                }
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                val err = _currentError.value
                                if (err != null) {
                                    Log.w("SyncManager", "Batch sync finished with recorded error: ${err.title}")
                                    _syncUiState.value = SyncUiState.ERROR(
                                        title = err.title,
                                        message = err.message,
                                        solution = err.solution,
                                        failedModule = err.failedModule,
                                        timestamp = err.timestamp
                                    )
                                } else {
                                    Log.d("SyncManager", "Batch sync SUCCEEDED -> IDLE")
                                    _syncUiState.value = SyncUiState.IDLE
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                val err = _currentError.value
                                if (err != null) {
                                    Log.w("SyncManager", "Batch sync FAILED with error: ${err.title}")
                                    _syncUiState.value = SyncUiState.ERROR(
                                        title = err.title,
                                        message = err.message,
                                        solution = err.solution,
                                        failedModule = err.failedModule,
                                        timestamp = err.timestamp
                                    )
                                } else {
                                    Log.w("SyncManager", "Batch sync FAILED -> OFFLINE")
                                    _syncUiState.value = SyncUiState.OFFLINE
                                }
                            }
                            WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {
                                val err = _currentError.value
                                if (err != null) {
                                    _syncUiState.value = SyncUiState.ERROR(
                                        title = err.title,
                                        message = err.message,
                                        solution = err.solution,
                                        failedModule = err.failedModule,
                                        timestamp = err.timestamp
                                    )
                                } else {
                                    _syncUiState.value = SyncUiState.IDLE
                                }
                            }
                        }
                    } else {
                        val err = _currentError.value
                        if (err != null) {
                            _syncUiState.value = SyncUiState.ERROR(
                                title = err.title,
                                message = err.message,
                                solution = err.solution,
                                failedModule = err.failedModule,
                                timestamp = err.timestamp
                            )
                        } else {
                            _syncUiState.value = SyncUiState.IDLE
                        }
                    }
                }
        }
    }

    fun recordSyncError(error: SyncErrorDetails) {
        _currentError.value = error
        _syncUiState.value = SyncUiState.ERROR(
            title = error.title,
            message = error.message,
            solution = error.solution,
            failedModule = error.failedModule,
            timestamp = error.timestamp
        )
    }

    fun clearSyncError() {
        _currentError.value = null
        _syncUiState.value = SyncUiState.IDLE
    }

    /**
     * Executes bidirectional sync immediately for all modules.
     * Guarded by a Mutex to avoid parallel overlapping runs.
     * Returns true if all modules succeeded, false if any module failed or a network error occurred.
     */
    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = NetworkClient.baseUrl
        val hasToken = NetworkClient.isLoggedIn()
        val familyId = NetworkClient.currentFamilyId
        val api = NetworkClient.apiService

        if (!hasToken) {
            Log.d("SyncManager", "Skipping sync: No active session")
            return@withContext true
        }

        syncMutex.withLock {
            Log.d("SyncManager", "Starting performSync() -> Host: $baseUrl | Family: $familyId")
            _syncUiState.value = SyncUiState.SYNCING

            val failedModules = mutableListOf<String>()
            var firstErrorDetails: SyncErrorDetails? = null

            fun reportModuleError(moduleName: String, code: Int, errorBody: String?) {
                failedModules.add(moduleName)
                if (firstErrorDetails == null) {
                    val (title, solution) = when (code) {
                        404 -> Pair(
                            "Módulo no disponible en servidor (404)",
                            "El servidor no reconoce la ruta de $moduleName. Es necesario actualizar y recompilar el contenedor Docker del servidor backend (100.97.53.61:8001)."
                        )
                        422 -> Pair(
                            "Error de validación o formato (422)",
                            "El servidor rechazó los datos de $moduleName por formato no válido. Actualiza la aplicación o verifica los campos ingresados."
                        )
                        401, 403 -> Pair(
                            "Sesión expirada ($code)",
                            "Tu sesión no es válida. Ve a Ajustes (ícono de engranaje) y vuelve a ingresar tu código familiar."
                        )
                        in 500..599 -> Pair(
                            "Error interno del servidor ($code)",
                            "El backend falló internamente con código $code al sincronizar $moduleName. Revisa los logs de Docker en el servidor."
                        )
                        else -> Pair(
                            "Error de respuesta ($code)",
                            "El servidor devolvió HTTP $code al sincronizar $moduleName."
                        )
                    }
                    val cleanMsg = errorBody?.take(180)?.ifBlank { null }
                        ?: "El servidor respondió con código HTTP $code para $moduleName."
                    firstErrorDetails = SyncErrorDetails(
                        title = title,
                        message = cleanMsg,
                        solution = solution,
                        failedModule = moduleName
                    )
                }
            }

            try {
                // 1. Sync Items (Shopping & Tasks) - Bidirectional
                val pendingItems = db.itemDao().getPendingSyncItems()
                val itemDtos = pendingItems.map { ItemDto.fromEntity(it) }
                val itemsRequest = BatchSyncItemsRequestDto(items = itemDtos)
                val itemsResponse = api.batchSyncItems(itemsRequest)

                if (itemsResponse.isSuccessful && itemsResponse.body() != null) {
                    val body = itemsResponse.body()!!
                    if (body.syncedIds.isNotEmpty()) {
                        db.itemDao().updateSyncStatus(body.syncedIds, SyncStatus.SYNCED)
                    }
                    if (body.serverItems.isNotEmpty()) {
                        val serverEntities = body.serverItems.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                        db.itemDao().upsertAll(serverEntities)
                    }
                } else {
                    val errorBody = itemsResponse.errorBody()?.string()
                    Log.e("SyncManager", "Items sync failed with HTTP ${itemsResponse.code()}: $errorBody")
                    reportModuleError("Compras y Tareas", itemsResponse.code(), errorBody)
                }

                // 2. Sync Calendar Events - Bidirectional
                val pendingEvents = db.calendarEventDao().getPendingSyncEvents()
                val eventDtos = pendingEvents.map { CalendarEventDto.fromEntity(it) }
                val eventsRequest = BatchSyncEventsRequestDto(events = eventDtos)
                val eventsResponse = api.batchSyncEvents(eventsRequest)

                if (eventsResponse.isSuccessful && eventsResponse.body() != null) {
                    val body = eventsResponse.body()!!
                    if (body.syncedIds.isNotEmpty()) {
                        db.calendarEventDao().updateSyncStatus(body.syncedIds, SyncStatus.SYNCED)
                    }
                    if (body.serverEvents.isNotEmpty()) {
                        val serverEntities = body.serverEvents.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                        db.calendarEventDao().upsertAll(serverEntities)
                    }
                } else {
                    val errorBody = eventsResponse.errorBody()?.string()
                    Log.e("SyncManager", "Events sync failed with HTTP ${eventsResponse.code()}: $errorBody")
                    reportModuleError("Calendario", eventsResponse.code(), errorBody)
                }

                // 3. Sync Finance (Accounts & Transactions) - Bidirectional
                val pendingAccounts = db.accountDao().getPendingSyncAccounts()
                val accountDtos = pendingAccounts.map { AccountDto.fromEntity(it) }
                val pendingTxs = db.financeTransactionDao().getPendingSyncTransactions()
                val txDtos = pendingTxs.map { FinanceTransactionDto.fromEntity(it) }

                val financeRequest = BatchSyncFinanceRequestDto(
                    accounts = accountDtos,
                    transactions = txDtos
                )
                val financeResponse = api.batchSyncFinance(financeRequest)

                if (financeResponse.isSuccessful && financeResponse.body() != null) {
                    val body = financeResponse.body()!!
                    if (body.syncedAccountIds.isNotEmpty()) {
                        db.accountDao().updateSyncStatus(body.syncedAccountIds, SyncStatus.SYNCED)
                    }
                    if (body.serverAccounts.isNotEmpty()) {
                        val serverAccEntities = body.serverAccounts.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                        db.accountDao().upsertAll(serverAccEntities)
                    }
                    if (body.syncedTransactionIds.isNotEmpty()) {
                        db.financeTransactionDao().updateSyncStatus(body.syncedTransactionIds, SyncStatus.SYNCED)
                    }
                    if (body.serverTransactions.isNotEmpty()) {
                        val serverTxEntities = body.serverTransactions.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                        db.financeTransactionDao().upsertAll(serverTxEntities)
                    }
                } else {
                    val errorBody = financeResponse.errorBody()?.string()
                    Log.e("SyncManager", "Finance sync failed with HTTP ${financeResponse.code()}: $errorBody")
                    reportModuleError("Finanzas", financeResponse.code(), errorBody)
                }

                if (failedModules.isNotEmpty() && firstErrorDetails != null) {
                    Log.w("SyncManager", "Batch sync finished with errors in: $failedModules")
                    recordSyncError(firstErrorDetails!!)
                    return@withContext false
                } else {
                    Log.d("SyncManager", "Batch sync completed successfully for all modules.")
                    clearSyncError()
                    return@withContext true
                }
            } catch (e: CancellationException) {
                Log.d("SyncManager", "performSync() was cancelled")
                throw e
            } catch (e: Exception) {
                Log.e("SyncManager", "Batch sync network/unexpected error: ${e.message}", e)
                val isNetwork = e is java.io.IOException
                val title = if (isNetwork) "Sin conexión con el servidor" else "Error inesperado de sincronización"
                val solution = if (isNetwork) {
                    "No se pudo conectar a $baseUrl. Comprueba que el servidor esté encendido y que estés conectado a la red local o a Tailscale."
                } else {
                    "Ocurrió una excepción al sincronizar: ${e.localizedMessage ?: e.message}"
                }
                val errorDetails = SyncErrorDetails(
                    title = title,
                    message = e.localizedMessage ?: e.message ?: "Fallo de conexión",
                    solution = solution,
                    failedModule = "Conexión"
                )
                recordSyncError(errorDetails)
                return@withContext false
            }
        }
    }

    /**
     * Schedules synchronization:
     * 1. Launches an immediate in-process sync for instantaneous UI responsiveness.
     * 2. Enqueues a WorkManager request with KEEP policy as background guarantee without network constraints that block private VPNs.
     */
    fun scheduleSync() {
        // Immediate in-process sync
        coroutineScope.launch {
            try {
                performSync()
            } catch (e: CancellationException) {
                // Ignore normal cancellation
            } catch (e: Exception) {
                Log.e("SyncManager", "Immediate sync launch error: ${e.message}")
            }
        }

        // Background WorkManager request as offline/background fallback
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()

        workManager.enqueueUniqueWork(
            "FamilyBatchSync",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    fun startRealtimeSync() {
        if (!NetworkClient.isLoggedIn()) {
            Log.d("SyncManager", "Skipping SSE: user is not logged in")
            return
        }

        if (sseJob?.isActive == true) return

        sseJob = coroutineScope.launch {
            val token = NetworkClient.authToken
            Log.d("SyncManager", "Starting SSE stream for family: ${NetworkClient.currentFamilyId}")

            sseClient.listenToEvents(token)
                .catch { e ->
                    Log.e("SyncManager", "SSE Stream error: ${e.message}", e)
                }
                .collect { sseEvent ->
                    when (sseEvent.type) {
                        "connected" -> {
                            Log.d("SyncManager", "SSE Connected to family channel")
                        }
                        "item_created", "item_updated" -> {
                            Log.d("SyncManager", "SSE item mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "item_deleted" -> {
                            try {
                                val itemDto = gson.fromJson(sseEvent.data, ItemDto::class.java)
                                if (itemDto?.id != null) {
                                    db.itemDao().deletePermanently(itemDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling item delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                        "event_created", "event_updated" -> {
                            Log.d("SyncManager", "SSE event mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "event_deleted" -> {
                            try {
                                val eventDto = gson.fromJson(sseEvent.data, CalendarEventDto::class.java)
                                if (eventDto?.id != null) {
                                    db.calendarEventDao().deletePermanently(eventDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling calendar event delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                        "account_created", "account_updated" -> {
                            Log.d("SyncManager", "SSE account mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "account_deleted" -> {
                            try {
                                val accDto = gson.fromJson(sseEvent.data, AccountDto::class.java)
                                if (accDto?.id != null) {
                                    db.accountDao().deletePermanently(accDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling account delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                        "finance_transaction_created", "finance_transaction_updated" -> {
                            Log.d("SyncManager", "SSE finance transaction mutation received -> triggering scheduleSync()")
                            scheduleSync()
                        }
                        "finance_transaction_deleted" -> {
                            try {
                                val txDto = gson.fromJson(sseEvent.data, FinanceTransactionDto::class.java)
                                if (txDto?.id != null) {
                                    db.financeTransactionDao().deletePermanently(txDto.id)
                                }
                            } catch (e: Exception) {
                                Log.e("SyncManager", "Error handling finance transaction delete: ${e.message}")
                            }
                            scheduleSync()
                        }
                    }
                }
        }
    }

    fun restartRealtimeSync() {
        stopRealtimeSync()
        startRealtimeSync()
    }

    fun stopRealtimeSync() {
        sseJob?.cancel()
        sseJob = null
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
