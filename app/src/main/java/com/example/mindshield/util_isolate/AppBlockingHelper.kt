package com.example.mindshield.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

object AppBlockingHelper {
    
    private const val TAG = "AppBlockingHelper"
    
    /**
     * Check if accessibility service is enabled for app blocking
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        
        if (accessibilityEnabled == 1) {
            val service = "${context.packageName}/.service.AppBlockingService"
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            return settingValue?.contains(service) == true
        }
        
        return false
    }
    
    /**
     * Request accessibility service permission
     */
    fun requestAccessibilityPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings", e)
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun checkAllPermissions(context: Context): Boolean {
        val usageStatsPermission = UsageStatsPermissionHelper.hasUsageStatsPermission(context)
        val accessibilityPermission = isAccessibilityServiceEnabled(context)
        
        Log.d(TAG, "Usage stats permission: $usageStatsPermission")
        Log.d(TAG, "Accessibility service enabled: $accessibilityPermission")
        
        return usageStatsPermission && accessibilityPermission
    }
    
    /**
     * Request all required permissions
     */
    fun requestAllPermissions(context: Context) {
        if (!UsageStatsPermissionHelper.hasUsageStatsPermission(context)) {
            UsageStatsPermissionHelper.requestUsageStatsPermission(context)
        }
        
        if (!isAccessibilityServiceEnabled(context)) {
            requestAccessibilityPermission(context)
        }
    }
    
    /**
     * Get a summary of missing permissions
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        if (!UsageStatsPermissionHelper.hasUsageStatsPermission(context)) {
            missingPermissions.add("Usage Stats Access")
        }
        
        if (!isAccessibilityServiceEnabled(context)) {
            missingPermissions.add("Accessibility Service")
        }
        
        return missingPermissions
    }
    
    /**
     * Check if app blocking is fully functional
     */
    fun isAppBlockingReady(context: Context): Boolean {
        return checkAllPermissions(context)
    }
} 