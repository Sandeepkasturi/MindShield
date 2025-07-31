package com.example.mindshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mindshield.R
import com.example.mindshield.data.repository.AppTimerRepository
import com.example.mindshield.util.DistractionBlockOverlay
import com.example.mindshield.util.AppUsageTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTimerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appTimerRepository: AppTimerRepository
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeApps = mutableSetOf<String>()
    private val appStartTimes = mutableMapOf<String, Long>()
    private val intervalOverlayShown = mutableSetOf<String>()
    
    companion object {
        private const val CHANNEL_ID = "app_timer_alerts"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_DISMISS = "com.example.mindshield.ACTION_TIMER_DISMISS"
        private const val ACTION_GO_HOME = "com.example.mindshield.ACTION_TIMER_GO_HOME"
    }
    
    init {
        createNotificationChannel()
    }
    
    fun startMonitoring() {
        scope.launch {
            // Check and reset daily usage at midnight
            while (true) {
                appTimerRepository.checkAndResetDailyUsage()
                delay(60_000L) // Check every minute
            }
        }
    }
    
    fun onAppStarted(packageName: String) {
        android.util.Log.d("AppTimerService", "onAppStarted: $packageName")
        scope.launch {
            val timer = appTimerRepository.getAppTimer(packageName)
            val isBlocked = appTimerRepository.isAppBlocked(packageName)
            
            if (isBlocked) {
                // App is blocked - let the AccessibilityService handle the blocking
                // We just log it here, the actual blocking happens in AppBlockingService
                android.util.Log.d("AppTimerService", "App $packageName is blocked - blocking will be handled by AccessibilityService")
            } else {
                activeApps.add(packageName)
                appStartTimes[packageName] = System.currentTimeMillis()
                
                // Sync with UsageStatsManager for accurate tracking
                syncUsageWithSystem(packageName)
                
                // Check for interval (halfway) warning - show gentle notification instead of overlay
                if (timer != null && timer.isEnabled && timer.dailyLimitMinutes > 1 && !intervalOverlayShown.contains(packageName)) {
                    val halfTimeMs = (timer.dailyLimitMinutes * 60_000L) / 2
                    launch {
                        delay(halfTimeMs)
                        // Check if app is still active and warning not shown
                        if (activeApps.contains(packageName) && !intervalOverlayShown.contains(packageName)) {
                            showGentleWarning(packageName, timer.appName, timer.dailyLimitMinutes)
                            intervalOverlayShown.add(packageName)
                        }
                    }
                }
            }
        }
    }
    
    fun onAppStopped(packageName: String) {
        android.util.Log.d("AppTimerService", "onAppStopped: $packageName")
        scope.launch {
            val startTime = appStartTimes[packageName]
            if (startTime != null) {
                val durationSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                if (durationSeconds > 0) {
                    appTimerRepository.incrementUsageSeconds(packageName, durationSeconds)
                }
                appStartTimes.remove(packageName)
            }
            activeApps.remove(packageName)
            intervalOverlayShown.remove(packageName)
            
            // Sync with UsageStatsManager for accurate tracking
            syncUsageWithSystem(packageName)
        }
    }
    

    

    
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Timer Alerts"
            val descriptionText = "Notifications for app timer limits"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show a gentle warning notification instead of intrusive overlay
     */
    private fun showGentleWarning(packageName: String, appName: String, dailyLimitMinutes: Int) {
        val message = "You've used half your daily limit for $appName. Consider taking a break!"
        
        // Show a gentle notification instead of overlay
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "app_timer_warnings"
            
            // Create notification channel if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "App Timer Warnings",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Gentle reminders about app usage limits"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("MindShield Reminder")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_warning)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(packageName.hashCode(), notification)
            
        } catch (e: Exception) {
            android.util.Log.e("AppTimerService", "Error showing gentle warning", e)
        }
    }
    
    /**
     * Sync app usage with system UsageStatsManager for accurate tracking
     */
    private suspend fun syncUsageWithSystem(packageName: String) {
        try {
            val systemUsageTime = AppUsageTracker.getAppUsageTime(context, packageName)
            val timer = appTimerRepository.getAppTimer(packageName)
            
            if (timer != null) {
                val systemUsageSeconds = (systemUsageTime / 1000).toInt()
                val currentUsageSeconds = timer.currentUsageSeconds ?: 0
                
                // If there's a significant difference, update our tracking
                if (kotlin.math.abs(systemUsageSeconds - currentUsageSeconds) > 30) { // 30 second threshold
                    android.util.Log.d("AppTimerService", "Syncing usage for $packageName: system=$systemUsageSeconds, local=$currentUsageSeconds")
                    
                    // Reset to system usage and add any additional time from current session
                    val startTime = appStartTimes[packageName]
                    val additionalSeconds = if (startTime != null) {
                        ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    } else 0
                    
                    val totalUsageSeconds = systemUsageSeconds + additionalSeconds
                    appTimerRepository.resetUsageToSeconds(packageName, totalUsageSeconds)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppTimerService", "Error syncing usage with system for $packageName", e)
        }
    }
    
    /**
     * Get accurate app usage time from system
     */
    fun getAccurateAppUsage(packageName: String): Long {
        return AppUsageTracker.getAppUsageTime(context, packageName)
    }
    
    /**
     * Check if app should be blocked based on system usage
     */
    suspend fun checkAndBlockApp(packageName: String): Boolean {
        val timer = appTimerRepository.getAppTimer(packageName) ?: return false
        if (!timer.isEnabled) return false
        
        // Get accurate usage from system
        val systemUsageTime = AppUsageTracker.getAppUsageTime(context, packageName)
        val systemUsageSeconds = (systemUsageTime / 1000).toInt()
        val limitSeconds = timer.dailyLimitMinutes * 60
        
        if (systemUsageSeconds >= limitSeconds) {
            android.util.Log.d("AppTimerService", "App $packageName should be blocked: usage=$systemUsageSeconds, limit=$limitSeconds")
            return true
        }
        
        return false
    }
    
    fun stop() {
        scope.cancel()
    }
} 