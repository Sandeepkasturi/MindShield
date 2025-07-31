package com.example.mindshield.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import com.example.mindshield.util.AppUsageTracker
import com.example.mindshield.util.DistractionBlockOverlay
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockingService : AccessibilityService() {
    
    private val TAG = "AppBlockingService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Inject
    lateinit var appTimerService: AppTimerService
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { accessibilityEvent ->
            when (accessibilityEvent.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = accessibilityEvent.packageName?.toString()
                    if (packageName != null) {
                        handleAppStateChange(packageName)
                    }
                }
            }
        }
    }
    
    override fun onInterrupt() {
        // Handle interruption
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Configure the accessibility service for app blocking
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        
        serviceInfo = info
        
        Log.d(TAG, "AppBlockingService connected")
        
        // Start periodic usage checks
        startPeriodicUsageChecks()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "AppBlockingService destroyed")
    }
    
    private fun handleAppStateChange(packageName: String) {
        scope.launch {
            try {
                // Get current active app from system
                val currentActiveApp = AppUsageTracker.getCurrentActiveApp(this@AppBlockingService)
                
                if (currentActiveApp == packageName) {
                    // App is being opened/activated
                    Log.d(TAG, "App opened: $packageName")
                    appTimerService.onAppStarted(packageName)
                    
                    // Check if app should be blocked immediately
                    val shouldBlock = appTimerService.checkAndBlockApp(packageName)
                    if (shouldBlock) {
                        Log.d(TAG, "App $packageName blocked - sending to home screen")
                        // Use the elegant "go home" approach instead of overlay
                        sendToHomeScreen()
                    }
                } else {
                    // App is being closed/deactivated
                    Log.d(TAG, "App closed: $packageName")
                    appTimerService.onAppStopped(packageName)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling app state change for $packageName", e)
            }
        }
    }
    
    private fun startPeriodicUsageChecks() {
        scope.launch {
            while (true) {
                try {
                    // Check all monitored apps every 30 seconds
                    val currentActiveApp = AppUsageTracker.getCurrentActiveApp(this@AppBlockingService)
                    
                    if (currentActiveApp != null) {
                        val shouldBlock = appTimerService.checkAndBlockApp(currentActiveApp)
                        if (shouldBlock) {
                            Log.d(TAG, "App $currentActiveApp blocked during periodic check - sending to home")
                            sendToHomeScreen()
                        }
                    }
                    
                    delay(30_000L) // Check every 30 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic usage check", e)
                    delay(60_000L) // Wait longer on error
                }
            }
        }
    }
    
    /**
     * Send user to home screen using AccessibilityService global action
     */
    private fun sendToHomeScreen() {
        try {
            // Use the elegant AccessibilityService approach to go home
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "Sent user to home screen")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending user to home screen", e)
            // Fallback: try using Intent approach
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(homeIntent)
                Log.d(TAG, "Used fallback method to send user home")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Fallback method also failed", fallbackException)
            }
        }
    }
    
    /**
     * Force check and block a specific app
     */
    fun forceCheckApp(packageName: String) {
        scope.launch {
            try {
                val shouldBlock = appTimerService.checkAndBlockApp(packageName)
                if (shouldBlock) {
                    Log.d(TAG, "App $packageName force blocked - sending to home")
                    sendToHomeScreen()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error force checking app $packageName", e)
            }
        }
    }
    
    /**
     * Get accurate usage for an app
     */
    fun getAppUsage(packageName: String): Long {
        return appTimerService.getAccurateAppUsage(packageName)
    }
} 