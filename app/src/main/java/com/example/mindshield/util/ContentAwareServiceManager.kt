package com.example.mindshield.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.mindshield.service.ContentAwareDetectionService

object ContentAwareServiceManager {
    private const val TAG = "ContentAwareServiceManager"

    fun startContentAwareDetection(context: Context) {
        try {
            // Check if accessibility service is enabled
            if (!isAccessibilityServiceEnabled(context)) {
                Log.d(TAG, "Accessibility service not enabled, requesting permission")
                requestAccessibilityPermission(context)
                return
            }

            // Start the content-aware detection service
            ContentAwareDetectionService.start(context)
            Log.d(TAG, "Content-aware detection service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting content-aware detection: ${e.message}")
        }
    }

    fun stopContentAwareDetection(context: Context) {
        try {
            ContentAwareDetectionService.stop(context)
            Log.d(TAG, "Content-aware detection service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping content-aware detection: ${e.message}")
        }
    }

    fun isContentAwareServiceEnabled(context: Context): Boolean {
        return try {
            ContentAwareDetectionService.isContentAwareServiceEnabled(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking content-aware service status: ${e.message}")
            false
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )

        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            return services?.contains("com.example.mindshield/.service.ContentAwareDetectionService") == true
        }
        
        return false
    }

    private fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
} 