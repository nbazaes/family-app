package com.familyapp.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = FamilyDatabase.getInstance(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = NetworkClient.baseUrl
        val hasToken = NetworkClient.isLoggedIn()
        val familyId = NetworkClient.currentFamilyId
        val api = NetworkClient.apiService
        val syncManager = SyncManager.getInstance(applicationContext)

        Log.d("SyncWorker", "Executing SyncWorker -> Host: $baseUrl | LoggedIn: $hasToken | Family: $familyId")

        if (!hasToken) {
            Log.w("SyncWorker", "Skipping sync: No active session. Please log in or register from the server settings icon.")
            return@withContext Result.success()
        }

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
            Log.d("SyncWorker", "Syncing items: ${pendingItems.size} pending local mutations to upload")

            val itemsRequest = BatchSyncItemsRequestDto(items = itemDtos)
            val itemsResponse = api.batchSyncItems(itemsRequest)

            if (itemsResponse.isSuccessful && itemsResponse.body() != null) {
                val body = itemsResponse.body()!!
                Log.d("SyncWorker", "Items sync OK: ${body.syncedIds.size} accepted by server, ${body.serverItems.size} items received from server")
                if (body.syncedIds.isNotEmpty()) {
                    db.itemDao().updateSyncStatus(body.syncedIds, SyncStatus.SYNCED)
                }
                if (body.serverItems.isNotEmpty()) {
                    val serverEntities = body.serverItems.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                    db.itemDao().upsertAll(serverEntities)
                }
            } else {
                val errorBody = itemsResponse.errorBody()?.string()
                Log.e("SyncWorker", "Items sync failed with HTTP ${itemsResponse.code()}: $errorBody")
                reportModuleError("Compras y Tareas", itemsResponse.code(), errorBody)
            }

            // 2. Sync Calendar Events - Bidirectional
            val pendingEvents = db.calendarEventDao().getPendingSyncEvents()
            val eventDtos = pendingEvents.map { CalendarEventDto.fromEntity(it) }
            Log.d("SyncWorker", "Syncing calendar: ${pendingEvents.size} pending local events to upload")

            val eventsRequest = BatchSyncEventsRequestDto(events = eventDtos)
            val eventsResponse = api.batchSyncEvents(eventsRequest)

            if (eventsResponse.isSuccessful && eventsResponse.body() != null) {
                val body = eventsResponse.body()!!
                Log.d("SyncWorker", "Events sync OK: ${body.syncedIds.size} accepted by server, ${body.serverEvents.size} events received from server")
                if (body.syncedIds.isNotEmpty()) {
                    db.calendarEventDao().updateSyncStatus(body.syncedIds, SyncStatus.SYNCED)
                }
                if (body.serverEvents.isNotEmpty()) {
                    val serverEntities = body.serverEvents.map { it.toEntity(familyId, SyncStatus.SYNCED) }
                    db.calendarEventDao().upsertAll(serverEntities)
                }
            } else {
                val errorBody = eventsResponse.errorBody()?.string()
                Log.e("SyncWorker", "Events sync failed with HTTP ${eventsResponse.code()}: $errorBody")
                reportModuleError("Calendario", eventsResponse.code(), errorBody)
            }

            // 3. Sync Finance (Accounts & Transactions) - Bidirectional
            val pendingAccounts = db.accountDao().getPendingSyncAccounts()
            val accountDtos = pendingAccounts.map { AccountDto.fromEntity(it) }
            val pendingTxs = db.financeTransactionDao().getPendingSyncTransactions()
            val txDtos = pendingTxs.map { FinanceTransactionDto.fromEntity(it) }
            Log.d("SyncWorker", "Syncing finance: ${pendingAccounts.size} pending accounts, ${pendingTxs.size} pending transactions to upload")

            val financeRequest = BatchSyncFinanceRequestDto(
                accounts = accountDtos,
                transactions = txDtos
            )
            val financeResponse = api.batchSyncFinance(financeRequest)

            if (financeResponse.isSuccessful && financeResponse.body() != null) {
                val body = financeResponse.body()!!
                Log.d("SyncWorker", "Finance sync OK: ${body.syncedAccountIds.size} accounts & ${body.syncedTransactionIds.size} txs accepted by server")
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
                Log.e("SyncWorker", "Finance sync failed with HTTP ${financeResponse.code()}: $errorBody")
                reportModuleError("Finanzas", financeResponse.code(), errorBody)
            }

            if (failedModules.isNotEmpty() && firstErrorDetails != null) {
                Log.w("SyncWorker", "Batch sync encountered errors in: $failedModules")
                syncManager.recordSyncError(firstErrorDetails!!)
                Result.failure()
            } else {
                Log.d("SyncWorker", "Batch sync completed successfully for all modules.")
                syncManager.clearSyncError()
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Batch sync encountered network error: ${e.message}", e)
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
            syncManager.recordSyncError(errorDetails)
            Result.retry()
        }
    }
}
