package com.familyapp.core.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.familyapp.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Checking(val isManual: Boolean) : UpdateUiState
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateUiState
    data class Downloading(
        val updateInfo: AppUpdateInfo,
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class ReadyToInstall(
        val updateInfo: AppUpdateInfo,
        val apkFile: File
    ) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "family_app_updater_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

        @Volatile
        private var instance: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val updateService = UpdateService()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun getCurrentVersionName(): String {
        return try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0.0.0"
            } catch (_: Exception) {
                "0.0.0"
            }
        }
    }

    /**
     * Checks for updates.
     * @param isManual true if triggered by user clicking a button, false for silent startup check
     */
    fun checkForUpdates(isManual: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)

        if (!isManual && (now - lastCheck < CHECK_INTERVAL_MS)) {
            Log.d(TAG, "Skipping automatic update check, checked recently (${(now - lastCheck) / 1000 / 60} min ago)")
            return
        }

        _uiState.value = UpdateUiState.Checking(isManual)

        scope.launch {
            try {
                val currentVersion = getCurrentVersionName()
                val updateInfo = updateService.checkLatestRelease(currentVersion)
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

                if (updateInfo != null) {
                    Log.i(TAG, "Update available: ${updateInfo.versionName} (current: $currentVersion)")
                    _uiState.value = UpdateUiState.UpdateAvailable(updateInfo)
                } else {
                    if (isManual) {
                        _uiState.value = UpdateUiState.UpToDate(currentVersion)
                    } else {
                        _uiState.value = UpdateUiState.Idle
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}", e)
                if (isManual) {
                    _uiState.value = UpdateUiState.Error("No se pudo comprobar actualizaciones: ${e.localizedMessage ?: e.message}")
                } else {
                    _uiState.value = UpdateUiState.Idle
                }
            }
        }
    }

    /**
     * Starts downloading the APK for the given [updateInfo].
     */
    fun startDownload(updateInfo: AppUpdateInfo) {
        val updatesDir = File(appContext.cacheDir, "updates")
        val apkFile = File(updatesDir, "FamilyApp-${updateInfo.versionName}.apk")

        _uiState.value = UpdateUiState.Downloading(
            updateInfo = updateInfo,
            progress = 0f,
            bytesRead = 0L,
            totalBytes = updateInfo.fileSizeBytes
        )

        scope.launch {
            try {
                updateService.downloadApk(
                    downloadUrl = updateInfo.downloadUrl,
                    destinationFile = apkFile
                ) { progress, bytesRead, totalBytes ->
                    _uiState.value = UpdateUiState.Downloading(
                        updateInfo = updateInfo,
                        progress = progress,
                        bytesRead = bytesRead,
                        totalBytes = totalBytes
                    )
                }

                _uiState.value = UpdateUiState.ReadyToInstall(updateInfo, apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _uiState.value = UpdateUiState.Error("Error al descargar actualización: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    /**
     * Attempts to install the downloaded APK.
     */
    fun installUpdate(apkFile: File) {
        if (!apkFile.exists()) {
            _uiState.value = UpdateUiState.Error("El archivo de actualización no existe")
            return
        }

        if (!UpdateInstaller.canInstallPackages(appContext)) {
            UpdateInstaller.openInstallPermissionSettings(appContext)
            return
        }

        UpdateInstaller.installApk(appContext, apkFile)
    }

    /**
     * Resets the update state back to Idle.
     */
    fun dismiss() {
        _uiState.value = UpdateUiState.Idle
    }
}
