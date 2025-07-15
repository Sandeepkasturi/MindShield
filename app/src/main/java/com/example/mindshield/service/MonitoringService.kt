package com.example.mindshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mindshield.R
import com.example.mindshield.data.model.AppUsage
import com.example.mindshield.data.repository.AppUsageRepository
import com.example.mindshield.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log

@AndroidEntryPoint
class MonitoringService : Service() {
    
    @Inject
    lateinit var appUsageRepository: AppUsageRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var distractionDetector: DistractionDetector
    
    private val usageStatsManager by lazy { 
        getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager 
    }
    private val packageManagerInstance by lazy { getPackageManager() } // Changed property name
    
    private var monitoringJob: Job? = null
    private var isMonitoring = false
    private var isScreenOn = true
    private var isUserPresent = true
    private var pollingIntervalMs = 10000L // 10 seconds instead of 5 seconds
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    tryStartMonitoring()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopMonitoring()
                }
                Intent.ACTION_USER_PRESENT -> {
                    isUserPresent = true
                    tryStartMonitoring()
                }
            }
        }
    }
    
    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp: StateFlow<String?> = _currentApp.asStateFlow()
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "mindshield_monitoring"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Register screen/user present receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        
        // Use RECEIVER_NOT_EXPORTED for API 26+, regular registerReceiver for older versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        return START_STICKY
    }
    
    inner class LocalBinder : android.os.Binder() {
        fun getService(): MonitoringService = this@MonitoringService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? = binder
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e("MonitoringService", "Error unregistering receiver: ${e.message}")
        }
        
        // Clean up resources
        cleanupResources()
    }
    
    private fun cleanupResources() {
        // Clear state variables
        isMonitoring = false
        isScreenOn = true
        isUserPresent = true
        
        // Cancel any ongoing jobs
        monitoringJob?.cancel()
        monitoringJob = null
        
        // Force garbage collection
        System.gc()
        Log.d("MonitoringService", "MonitoringService resources cleaned up")
    }

    private fun tryStartMonitoring() {
        if (isScreenOn && isUserPresent) {
            startMonitoring()
        } else {
            stopMonitoring()
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isMonitoring && isScreenOn && isUserPresent) {
                monitorAppUsage()
                delay(pollingIntervalMs)
            }
        }
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    private suspend fun monitorAppUsage() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 // Last second
        
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var currentPackage: String? = null
        var currentStartTime: Long = 0
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    currentPackage = event.packageName
                    currentStartTime = event.timeStamp
                    _currentApp.value = currentPackage
                    
                    // Check if this is a distracting app
                    val isDistracting = isDistractingApp(currentPackage)
                    if (isDistracting) {
                        distractionDetector.startTracking(currentPackage, currentStartTime)
                    }
                }
                
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentPackage != null && currentStartTime > 0) {
                        val duration = (event.timeStamp - currentStartTime) / 1000 / 60 // Convert to minutes
                        
                        if (duration > 0) {
                            val appName = getAppName(currentPackage)
                            val isDistracting = isDistractingApp(currentPackage)
                            
                            val appUsage = AppUsage(
                                packageName = currentPackage,
                                appName = appName,
                                startTime = currentStartTime,
                                endTime = event.timeStamp,
                                duration = duration,
                                isDistracting = isDistracting
                            )
                            
                            appUsageRepository.insertAppUsage(appUsage)
                        }
                        
                        distractionDetector.stopTracking(currentPackage)
                    }
                    
                    currentPackage = null
                    currentStartTime = 0
                    _currentApp.value = null
                }
            }
        }
    }
    
    private suspend fun isDistractingApp(packageName: String?): Boolean {
        if (packageName == null) return false
        
        val distractingApps = settingsRepository.distractingApps.first()
        return distractingApps.contains(packageName)
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = packageManagerInstance.getApplicationInfo(packageName, 0) // Use the renamed property
            packageManagerInstance.getApplicationLabel(applicationInfo).toString() // Use the renamed property
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MindShield Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage for distractions"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MindShield Active")
            .setContentText("Monitoring app usage")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
} 