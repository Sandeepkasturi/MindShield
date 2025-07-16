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
                showTomatoLimitOverlay(packageName)
                // Immediately send to home to prevent app usage
                goToHome()
            } else {
                activeApps.add(packageName)
                appStartTimes[packageName] = System.currentTimeMillis()
                // Check for interval (halfway) overlay
                if (timer != null && timer.isEnabled && timer.dailyLimitMinutes > 1 && !intervalOverlayShown.contains(packageName)) {
                    val halfTimeMs = (timer.dailyLimitMinutes * 60_000L) / 2
                    launch {
                        delay(halfTimeMs)
                        // Check if app is still active and overlay not shown
                        if (activeApps.contains(packageName) && !intervalOverlayShown.contains(packageName)) {
                            showIntervalOverlay(packageName, timer.appName, timer.dailyLimitMinutes)
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
        }
    }
    
    private suspend fun showTimeLimitNotification(packageName: String) {
        val appName = getAppName(packageName)
        // --- Removed system notification logic ---
        // Show overlay only (tomato background)
        DistractionBlockOverlay.show(
            context,
            appName,
            5000L, // 5 seconds
            onDismiss = {
                // After overlay dismisses, go to home
                goToHome()
            },
            message = "You used your limit for $appName today",
            color = 0xFFFF6347.toInt() // Tomato red
        )
    }
    
    private fun goToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(homeIntent)
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
    
    private fun showIntervalOverlay(packageName: String, appName: String, dailyLimitMinutes: Int) {
        val message = "You used your half time on the $appName. Take a break!"
        // Show tomato overlay (instead of orange)
        DistractionBlockOverlay.show(
            context,
            appName,
            5000L,
            onDismiss = {},
            message = message,
            color = 0xFFFF6347.toInt() // Tomato red
        )
    }

    private suspend fun showTomatoLimitOverlay(packageName: String) {
        val appName = getAppName(packageName)
        val message = "You used your limit for $appName today"
        // Show tomato overlay (red color)
        withContext(Dispatchers.Main) {
            DistractionBlockOverlay.show(
                context,
                appName,
                5000L, // 5 seconds
                onDismiss = {
                    // After overlay, always go to home
                    goToHome()
                },
                message = message,
                color = 0xFFFF6347.toInt() // Tomato red
            )
        }
    }
    
    private suspend fun showTomatoHalfTimeOverlay(packageName: String, appName: String) {
        val message = "You used your half time on $appName. Consider take break"
        withContext(Dispatchers.Main) {
            DistractionBlockOverlay.show(
                context,
                appName,
                5000L, // 5 seconds
                onDismiss = {},
                message = message,
                color = 0xFFFF6347.toInt() // Tomato red
            )
        }
    }
    
    fun stop() {
        scope.cancel()
    }
} 