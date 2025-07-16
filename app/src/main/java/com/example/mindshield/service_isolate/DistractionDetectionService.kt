package com.example.mindshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.mindshield.R
import com.example.mindshield.data.repository.SettingsRepository
import com.example.mindshield.MainActivity
import com.example.mindshield.data.model.AppInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.content.BroadcastReceiver
import android.content.IntentFilter

@HiltWorker
class DistractionDetectionService @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val distractingApps = mutableSetOf<String>()
    private val appStartTimes = mutableMapOf<String, Long>()
    private val appUsageTimers = mutableMapOf<String, Runnable>()
    
    private var monitoringJob: Job? = null
    private val pollingIntervalMs = 10_000L // 10 seconds
    private val appInfoCache = ConcurrentHashMap<String, AppInfo>()
    private var youtubeDistracting: Boolean = false
    
    companion object {
        private const val CHANNEL_ID = "distraction_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val OVERLAY_DELAY_MS = 60000L // 60 seconds
        private const val BLOCK_DURATION_MS = 300000L // 5 minutes
        
        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            
            val request = PeriodicWorkRequestBuilder<DistractionDetectionService>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "distraction_detection",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }
        
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("distraction_detection")
        }
    }

    override suspend fun doWork(): Result {
        // Check if app is in foreground, skip work if so
        val prefs = applicationContext.getSharedPreferences("mindshield_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_foreground", false)) {
            return Result.success() // App is open, skip background work
        }
        try {
            setupNotificationChannel()
            loadDistractingApps()
            startMonitoring()
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private suspend fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Distraction Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for distracting app usage"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun loadDistractingApps() {
        distractingApps.clear()
        distractingApps.addAll(settingsRepository.distractingApps.first())
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                checkCurrentApp()
                delay(pollingIntervalMs)
            }
        }
    }

    private fun checkCurrentApp() {
        val currentTime = System.currentTimeMillis()
        val endTime = currentTime
        val startTime = endTime - pollingIntervalMs

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val packageName = event.packageName
                    if (distractingApps.contains(packageName)) {
                        onDistractingAppStarted(packageName, currentTime)
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val packageName = event.packageName
                    if (distractingApps.contains(packageName)) {
                        onDistractingAppStopped(packageName)
                    }
                }
            }
        }
    }

    private fun onDistractingAppStarted(packageName: String, startTime: Long) {
        appStartTimes[packageName] = startTime
        // Cancel any existing timer for this app
        appUsageTimers[packageName]?.let { /* no-op, handled by polling now */ }
        // Only show notification for YouTube if flagged as distracting
        if (packageName == "com.google.android.youtube") {
            if (youtubeDistracting) {
                showDistractionNotification(packageName, false)
            }
        } else {
            showDistractionNotification(packageName, false)
        }
    }

    private fun onDistractingAppStopped(packageName: String) {
        appStartTimes.remove(packageName)
        appUsageTimers.remove(packageName)
    }

    private fun onDistractingAppTimeout(packageName: String) {
        // Show blocking overlay after 1 minute
        showDistractionNotification(packageName, true)
        startBlockingOverlay(packageName)
    }

    private fun showDistractionNotification(packageName: String, isBlocking: Boolean) {
        val appInfo = getAppInfo(packageName)
        val title = if (isBlocking) "App Blocked" else "Distracting App Detected"
        val message = if (isBlocking) {
            "You've been using ${appInfo.appName} for too long. App is now blocked for 5 minutes."
        } else {
            "You are now using distracting app: ${appInfo.appName}"
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startBlockingOverlay(packageName: String) {
        // Launch overlay activity
        val appInfo = getAppInfo(packageName)
        val intent = Intent(applicationContext, com.example.mindshield.ui.activity.DistractionOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("package_name", packageName)
            putExtra("app_name", appInfo.appName)
        }
        applicationContext.startActivity(intent)
    }

    private fun getAppInfo(packageName: String): AppInfo {
        return appInfoCache.getOrPut(packageName) {
            val pm = applicationContext.packageManager
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
            val icon = try {
                pm.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
            // Determine category based on package name
            val category = when {
                packageName.contains("facebook") || packageName.contains("instagram") || 
                packageName.contains("twitter") || packageName.contains("snapchat") ||
                packageName.contains("whatsapp") || packageName.contains("discord") ||
                packageName.contains("reddit") || packageName.contains("youtube") ||
                packageName.contains("netflix") || packageName.contains("spotify") ||
                packageName.contains("tiktok") -> com.example.mindshield.data.model.AppCategory.UNPRODUCTIVE
                packageName.contains("docs") || packageName.contains("microsoft") ||
                packageName.contains("teams") || packageName.contains("keep") ||
                packageName.contains("todoist") || packageName.contains("evernote") ||
                packageName.contains("classroom") -> com.example.mindshield.data.model.AppCategory.PRODUCTIVE
                else -> com.example.mindshield.data.model.AppCategory.NEUTRAL
            }
            AppInfo(packageName, appName, icon, category, 0L)
        }
    }

    // Add a broadcast receiver to listen for distracting search/content for YouTube
    private val youtubeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.mindshield.CONTENT_ANALYSIS_RESULT") {
                val isDistracting = intent.getBooleanExtra("isDistracting", false)
                val packageName = intent.getStringExtra("packageName")
                if (packageName == "com.google.android.youtube") {
                    youtubeDistracting = isDistracting
                }
            }
        }
    }
    // Register this receiver in the service's onCreate or initialization logic:
    // registerReceiver(youtubeBroadcastReceiver, IntentFilter("com.example.mindshield.CONTENT_ANALYSIS_RESULT"), Context.RECEIVER_NOT_EXPORTED)
} 