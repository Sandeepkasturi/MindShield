package com.example.mindshield.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import com.example.mindshield.util.DistractionSessionManager
import com.example.mindshield.util.DistractionAppRepository
import com.example.mindshield.util.DistractionNotificationHelper
import com.example.mindshield.util.DistractionBlockOverlay
import com.example.mindshield.util.AppUsageTracker
import com.example.mindshield.service.AppTimerService

@AndroidEntryPoint
class DistractionAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            event?.let { accessibilityEvent ->
                when (accessibilityEvent.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                        val packageName = accessibilityEvent.packageName?.toString()
                        if (packageName != null) {
                            try {
                                // Handle distraction detection
                                val isDistracting = DistractionAppRepository.isDistractingApp(this, packageName)
                                if (isDistracting) {
                                    DistractionSessionManager.onAppOpened(this, packageName)
                                } else {
                                    DistractionSessionManager.resetSession()
                                }
                                
                                // Handle app timer monitoring
                                handleAppTimerMonitoring(packageName)
                                
                            } catch (e: Exception) {
                                android.util.Log.e("DistractionAccessibilityService", "Error in accessibility event handling: ", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DistractionAccessibilityService", "Error in onAccessibilityEvent: ", e)
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Configure the accessibility service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        
        serviceInfo = info
        
        // Start the distraction detection service
        DistractionDetectionService.start(this)

        // Start the persistent foreground service
        ForegroundService.start(this)

        // Set up session manager callbacks
        DistractionSessionManager.setCallbacks(
            onBlock = { packageName ->
                try {
                    val appName = DistractionAppRepository.getAppName(this, packageName)
                    DistractionBlockOverlay.show(
                        this,
                        appName,
                        30_000L,
                        onDismiss = {
                            // Dismiss logic if any
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("DistractionAccessibilityService", "Error in onBlock: ", e)
                }
            },
            onUnblock = { packageName ->
                try {
                    DistractionBlockOverlay.remove(this)
                } catch (e: Exception) {
                    android.util.Log.e("DistractionAccessibilityService", "Error in onUnblock: ", e)
                }
            },
            onWarning = { packageName ->
                try {
                    val appName = DistractionAppRepository.getAppName(this, packageName)
                    // DistractionNotificationHelper.showWarningNotification(this, appName) {
                    //     DistractionSessionManager.onNotificationDismissed(packageName)
                    // }
                } catch (e: Exception) {
                    android.util.Log.e("DistractionAccessibilityService", "Error in onWarning: ", e)
                }
            },
            onNotificationDismiss = { packageName ->
                // Optionally log or handle notification dismissal
            },
            onOverlayFinish = { packageName ->
                // Send user to home screen using AccessibilityService global action
                try {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } catch (e: Exception) {
                    android.util.Log.e("DistractionAccessibilityService", "Error in onOverlayFinish: ", e)
                    // Fallback to Intent approach
                    try {
                        val intent = Intent(Intent.ACTION_MAIN)
                        intent.addCategory(Intent.CATEGORY_HOME)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    } catch (fallbackException: Exception) {
                        android.util.Log.e("DistractionAccessibilityService", "Fallback method also failed", fallbackException)
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the distraction detection service
        DistractionDetectionService.stop(this)
        // Stop the persistent foreground service
        ForegroundService.stop(this)
    }

    /**
     * Handle app timer monitoring for usage limits
     */
    private fun handleAppTimerMonitoring(packageName: String) {
        try {
            // Get the current active app from system
            val currentActiveApp = AppUsageTracker.getCurrentActiveApp(this)
            
            if (currentActiveApp == packageName) {
                // App is being opened/activated
                AppTimerService.onAppStarted(packageName)
            } else {
                // App is being closed/deactivated
                AppTimerService.onAppStopped(packageName)
            }
            
            // Check if app should be blocked based on usage
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val isBlocked = AppTimerService.checkAndBlockApp(packageName)
                if (isBlocked) {
                    android.util.Log.d("DistractionAccessibilityService", "App $packageName blocked due to usage limit")
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DistractionAccessibilityService", "Error in app timer monitoring: ", e)
        }
    }
    
    private fun notifyAppChange(packageName: String) {
        // No longer used; logic moved to onAccessibilityEvent
    }

    // TODO: Wire DistractionSessionManager callbacks to show notification, overlay, and handle notification dismissal
} 