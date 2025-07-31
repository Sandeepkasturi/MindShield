package com.example.mindshield.util

import android.content.Context
import android.util.Log
import com.example.mindshield.data.model.AppTimer
import com.example.mindshield.data.repository.AppTimerRepository
import com.example.mindshield.service.AppTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlockingManager @Inject constructor(
    private val context: Context,
    private val appTimerRepository: AppTimerRepository,
    private val appTimerService: AppTimerService
) {
    
    private val TAG = "AppBlockingManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Initialize app blocking system
     */
    fun initialize() {
        if (!AppBlockingHelper.isAppBlockingReady(context)) {
            Log.w(TAG, "App blocking not ready - missing permissions")
            return
        }
        
        Log.d(TAG, "Initializing app blocking system")
        appTimerService.startMonitoring()
    }
    
    /**
     * Add an app to be monitored with a daily time limit
     */
    fun addAppTimer(packageName: String, appName: String, dailyLimitMinutes: Int) {
        scope.launch {
            try {
                val appTimer = AppTimer(
                    packageName = packageName,
                    appName = appName,
                    dailyLimitMinutes = dailyLimitMinutes,
                    isEnabled = true
                )
                
                appTimerRepository.insertAppTimer(appTimer)
                Log.d(TAG, "Added app timer for $appName: $dailyLimitMinutes minutes")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding app timer for $packageName", e)
            }
        }
    }
    
    /**
     * Remove an app from monitoring
     */
    fun removeAppTimer(packageName: String) {
        scope.launch {
            try {
                appTimerRepository.deleteAppTimerByPackage(packageName)
                Log.d(TAG, "Removed app timer for $packageName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error removing app timer for $packageName", e)
            }
        }
    }
    
    /**
     * Update an app's daily limit
     */
    fun updateAppLimit(packageName: String, newLimitMinutes: Int) {
        scope.launch {
            try {
                val timer = appTimerRepository.getAppTimer(packageName)
                if (timer != null) {
                    val updatedTimer = timer.copy(dailyLimitMinutes = newLimitMinutes)
                    appTimerRepository.updateAppTimer(updatedTimer)
                    Log.d(TAG, "Updated limit for $packageName: $newLimitMinutes minutes")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating app limit for $packageName", e)
            }
        }
    }
    
    /**
     * Enable or disable monitoring for an app
     */
    fun setAppMonitoringEnabled(packageName: String, enabled: Boolean) {
        scope.launch {
            try {
                val timer = appTimerRepository.getAppTimer(packageName)
                if (timer != null) {
                    val updatedTimer = timer.copy(isEnabled = enabled)
                    appTimerRepository.updateAppTimer(updatedTimer)
                    Log.d(TAG, "Set monitoring for $packageName: $enabled")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting monitoring for $packageName", e)
            }
        }
    }
    
    /**
     * Get current usage for an app
     */
    fun getAppUsage(packageName: String): Long {
        return appTimerService.getAccurateAppUsage(packageName)
    }
    
    /**
     * Reset usage for an app
     */
    fun resetAppUsage(packageName: String) {
        scope.launch {
            try {
                appTimerRepository.resetUsage(packageName, System.currentTimeMillis())
                Log.d(TAG, "Reset usage for $packageName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting usage for $packageName", e)
            }
        }
    }
    
    /**
     * Check if an app is currently blocked
     */
    suspend fun isAppBlocked(packageName: String): Boolean {
        return appTimerRepository.isAppBlocked(packageName)
    }
    
    /**
     * Force check and block an app if needed
     */
    fun forceCheckApp(packageName: String) {
        scope.launch {
            try {
                val isBlocked = appTimerService.checkAndBlockApp(packageName)
                if (isBlocked) {
                    Log.d(TAG, "App $packageName force blocked")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error force checking app $packageName", e)
            }
        }
    }
    
    /**
     * Get all monitored apps
     */
    fun getAllMonitoredApps() = appTimerRepository.getAllAppTimers()
    
    /**
     * Get enabled monitored apps
     */
    suspend fun getEnabledMonitoredApps() = appTimerRepository.getEnabledAppTimers()
    
    /**
     * Check if app blocking system is ready
     */
    fun isReady(): Boolean {
        return AppBlockingHelper.isAppBlockingReady(context)
    }
    
    /**
     * Request all required permissions
     */
    fun requestPermissions() {
        AppBlockingHelper.requestAllPermissions(context)
    }
    
    /**
     * Get missing permissions
     */
    fun getMissingPermissions(): List<String> {
        return AppBlockingHelper.getMissingPermissions(context)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        appTimerService.stop()
    }
} 