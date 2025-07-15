package com.example.mindshield.util

import android.content.Context
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.util.Log

object DistractionAppRepository {
    private val TAG = "DistractionAppRepo"
    
    // Default distraction apps
    private val defaultApps = setOf(
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.instagram.android" // Instagram is now explicitly included
        // Add more as needed
    )

    fun getAllDistractionApps(context: Context): Set<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val userApps = prefs.getStringSet("user_distraction_apps", emptySet()) ?: emptySet()
        val allApps = defaultApps + userApps
        Log.d(TAG, "Distraction apps: $allApps")
        return allApps
    }

    fun addUserDistractionApp(context: Context, packageName: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val userApps = prefs.getStringSet("user_distraction_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        userApps.add(packageName)
        prefs.edit().putStringSet("user_distraction_apps", userApps).apply()
        Log.d(TAG, "Added user distraction app: $packageName")
    }

    fun removeUserDistractionApp(context: Context, packageName: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val userApps = prefs.getStringSet("user_distraction_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        userApps.remove(packageName)
        prefs.edit().putStringSet("user_distraction_apps", userApps).apply()
        Log.d(TAG, "Removed user distraction app: $packageName")
    }

    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            Log.d(TAG, "App name for $packageName: $appName")
            appName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not find app name for $packageName: ${e.message}")
            packageName
        }
    }

    fun isDistractingApp(context: Context, packageName: String): Boolean {
        val distractionApps = getAllDistractionApps(context)
        return distractionApps.contains(packageName)
    }
} 