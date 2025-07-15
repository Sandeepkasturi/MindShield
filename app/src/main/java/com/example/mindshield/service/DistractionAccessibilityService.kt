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

@AndroidEntryPoint
class DistractionAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { accessibilityEvent ->
            when (accessibilityEvent.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val packageName = accessibilityEvent.packageName?.toString()
                    if (packageName != null) {
                        val isDistracting = DistractionAppRepository.isDistractingApp(this, packageName)
                        if (isDistracting) {
                            DistractionSessionManager.onAppOpened(this, packageName)
                        } else {
                            DistractionSessionManager.resetSession()
                        }
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

        // Set up session manager callbacks
        DistractionSessionManager.setCallbacks(
            onBlock = { packageName ->
                val appName = DistractionAppRepository.getAppName(this, packageName)
                val remaining = 30_000L // Always 30s for overlay
                DistractionBlockOverlay.show(this, appName, remaining) {}
            },
            onUnblock = { packageName ->
                DistractionBlockOverlay.remove(this)
            },
            onWarning = { packageName ->
                val appName = DistractionAppRepository.getAppName(this, packageName)
                DistractionNotificationHelper.showWarningNotification(this, appName) {
                    DistractionSessionManager.onNotificationDismissed(packageName)
                }
            },
            onNotificationDismiss = { packageName ->
                // Optionally log or handle notification dismissal
            },
            onOverlayFinish = { packageName ->
                // Send user to home screen
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the distraction detection service
        DistractionDetectionService.stop(this)
    }

    private fun notifyAppChange(packageName: String) {
        // No longer used; logic moved to onAccessibilityEvent
    }

    // TODO: Wire DistractionSessionManager callbacks to show notification, overlay, and handle notification dismissal
} 