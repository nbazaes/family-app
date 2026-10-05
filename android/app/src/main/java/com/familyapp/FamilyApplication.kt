package com.familyapp

import android.app.Application
import android.util.Log
import com.familyapp.core.network.NetworkClient
import com.familyapp.sync.SyncManager

class FamilyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("FamilyApplication", "Initializing FamilyApp...")
        // 1. Initialize NetworkClient with preferences
        NetworkClient.init(this)
        // 2. Initialize ThemePreferences
        com.familyapp.core.ThemePreferences.init(this)
        // 3. Initialize and trigger SyncManager
        SyncManager.getInstance(this).scheduleSync()
    }
}

