package com.example.mindshield.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.util.Log

object UsageStatsPermissionHelper {
    
    private const val TAG = "UsageStatsPermissionHelper"
    
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        val hasPermission = mode == AppOpsManager.MODE_ALLOWED
        Log.d(TAG, "Usage stats permission: $hasPermission")
        return hasPermission
    }
    
    fun requestUsageStatsPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening usage access settings", e)
        }
    }
    
    fun checkAndRequestPermission(context: Context): Boolean {
        return if (hasUsageStatsPermission(context)) {
            true
        } else {
            Log.w(TAG, "Usage stats permission not granted, requesting...")
            requestUsageStatsPermission(context)
            false
        }
    }
} 