package com.familyapp.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val syncManager = SyncManager.getInstance(applicationContext)
        Log.d("SyncWorker", "Executing SyncWorker in background")

        try {
            val success = syncManager.performSync()
            if (success) {
                Log.d("SyncWorker", "SyncWorker finished successfully")
                Result.success()
            } else {
                Log.w("SyncWorker", "SyncWorker finished with failure/errors")
                Result.failure()
            }
        } catch (e: CancellationException) {
            Log.d("SyncWorker", "SyncWorker was cancelled")
            throw e
        } catch (e: Exception) {
            Log.e("SyncWorker", "SyncWorker encountered unexpected error: ${e.message}", e)
            Result.failure()
        }
    }
}
