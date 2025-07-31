package com.example.mindshield.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import java.util.*

object AppUsageTracker {
    
    private const val TAG = "AppUsageTracker"
    
    /**
     * Get the total foreground time for a specific app for the current day
     */
    fun getAppUsageTime(context: Context, packageName: String): Long {
        if (!UsageStatsPermissionHelper.hasUsageStatsPermission(context)) {
            Log.w(TAG, "Usage stats permission not granted for $packageName")
            return 0L
        }
        
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()
            
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            var totalTime: Long = 0
            var lastEventTime: Long = 0
            var appInForeground = false
            
            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)
                
                if (event.packageName == packageName) {
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            appInForeground = true
                            lastEventTime = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            if (appInForeground) {
                                totalTime += (event.timeStamp - lastEventTime)
                            }
                            appInForeground = false
                        }
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            if (appInForeground) {
                                totalTime += (event.timeStamp - lastEventTime)
                            }
                            appInForeground = false
                        }
                    }
                }
            }
            
            // If the app is still in the foreground, add the time until now
            if (appInForeground) {
                totalTime += (System.currentTimeMillis() - lastEventTime)
            }
            
            Log.d(TAG, "App usage for $packageName: ${totalTime / 1000} seconds")
            return totalTime
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app usage for $packageName", e)
            return 0L
        }
    }
    
    /**
     * Get app usage for a specific time range
     */
    fun getAppUsageTimeRange(context: Context, packageName: String, startTime: Long, endTime: Long): Long {
        if (!UsageStatsPermissionHelper.hasUsageStatsPermission(context)) {
            Log.w(TAG, "Usage stats permission not granted for $packageName")
            return 0L
        }
        
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            var totalTime: Long = 0
            var lastEventTime: Long = 0
            var appInForeground = false
            
            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)
                
                if (event.packageName == packageName) {
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            appInForeground = true
                            lastEventTime = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            if (appInForeground) {
                                totalTime += (event.timeStamp - lastEventTime)
                            }
                            appInForeground = false
                        }
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            if (appInForeground) {
                                totalTime += (event.timeStamp - lastEventTime)
                            }
                            appInForeground = false
                        }
                    }
                }
            }
            
            return totalTime
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app usage for $packageName in time range", e)
            return 0L
        }
    }
    
    /**
     * Get the currently active app package name
     */
    fun getCurrentActiveApp(context: Context): String? {
        if (!UsageStatsPermissionHelper.hasUsageStatsPermission(context)) {
            return null
        }
        
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000 // Look back 1 second
            
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            var lastResumedApp: String? = null
            
            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)
                
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastResumedApp = event.packageName
                }
            }
            
            return lastResumedApp
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current active app", e)
            return null
        }
    }
    
    /**
     * Get all apps used in the current day
     */
    fun getAppsUsedToday(context: Context): List<String> {
        if (!UsageStatsPermissionHelper.hasUsageStatsPermission(context)) {
            return emptyList()
        }
        
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()
            
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val usedApps = mutableSetOf<String>()
            
            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)
                
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    usedApps.add(event.packageName)
                }
            }
            
            return usedApps.toList()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting apps used today", e)
            return emptyList()
        }
    }
} 