package com.example.mindshield.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshield.data.model.AppInfo
import com.example.mindshield.data.model.AppCategory
import com.example.mindshield.data.repository.AppUsageRepository
import com.example.mindshield.data.repository.DistractionEventRepository
import com.example.mindshield.data.repository.SettingsRepository
import com.example.mindshield.service.DistractionDetectionService
import com.example.mindshield.service.DistractionDetector
import com.example.mindshield.service.MonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.usage.UsageStatsManager
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import android.util.Log

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    private val distractionEventRepository: DistractionEventRepository,
    private val settingsRepository: SettingsRepository,
    private val distractionDetector: DistractionDetector,
    private val application: Application
) : ViewModel() {

    private val _recentApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentApps: StateFlow<List<AppInfo>> = _recentApps.asStateFlow()

    private val _topApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val topApps: StateFlow<List<AppInfo>> = _topApps.asStateFlow()

    private val _todayOverview = MutableStateFlow<AppUsageRepository.TodayOverview?>(null)
    val todayOverview: StateFlow<AppUsageRepository.TodayOverview?> = _todayOverview.asStateFlow()

    private val _realTimeStats = MutableStateFlow<AppUsageRepository.RealTimeStats?>(null)
    val realTimeStats: StateFlow<AppUsageRepository.RealTimeStats?> = _realTimeStats.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _currentSession = MutableStateFlow<com.example.mindshield.data.model.AppSession?>(null)
    val currentSession: StateFlow<com.example.mindshield.data.model.AppSession?> = _currentSession.asStateFlow()

    private val _currentAppInfo = MutableStateFlow<AppInfo?>(null)
    val currentAppInfo: StateFlow<AppInfo?> = _currentAppInfo.asStateFlow()
    private var monitoringService: MonitoringService? = null
    private var currentAppStartTime: Long = 0L
    private var isPollingRecentApps = false

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MonitoringService.LocalBinder
            monitoringService = binder?.getService()
            observeCurrentApp()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            monitoringService = null
        }
    }

    fun bindMonitoringService(context: Context) {
        val intent = Intent(context, MonitoringService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindMonitoringService(context: Context) {
        context.unbindService(serviceConnection)
    }

    private fun observeCurrentApp() {
        monitoringService?.currentApp?.let { flow ->
            viewModelScope.launch {
                flow.collect { pkg ->
                    if (pkg != null) {
                        val pm = application.packageManager
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (e: Exception) { pkg }
                        val icon = try {
                            pm.getApplicationIcon(pkg)
                        } catch (e: Exception) { null }
                        val category = AppCategory.NEUTRAL // Optionally categorize
                        if (_currentAppInfo.value?.packageName != pkg) {
                            currentAppStartTime = System.currentTimeMillis()
                        }
                        val usageTime = System.currentTimeMillis() - currentAppStartTime
                        _currentAppInfo.value = AppInfo(pkg, appName, icon, category, usageTime)
                    } else {
                        _currentAppInfo.value = null
                        currentAppStartTime = 0L
                    }
                }
            }
        }
    }

    // New function to start real-time app usage polling
    fun startRecentAppsPolling(context: Context) {
        if (isPollingRecentApps) return
        isPollingRecentApps = true
        
        viewModelScope.launch {
            while (isPollingRecentApps) {
                try {
                    _recentApps.value = getRecentApps(context)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error polling recent apps: ${e.message}")
                }
                delay(3000) // Update every 3 seconds instead of 1 second
            }
        }
    }

    // New function to stop polling
    fun stopRecentAppsPolling() {
        isPollingRecentApps = false
    }

    // New function to get recent apps with real usage data
    private fun getRecentApps(context: Context): List<AppInfo> {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 1000 * 60 * 60 // Last 1 hour
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, beginTime, endTime
            )
            
            usageStats
                .filter { it.lastTimeUsed > beginTime && it.totalTimeInForeground > 0 }
                .sortedByDescending { it.lastTimeUsed }
                .take(10) // Limit to 10 most recent apps
                .mapNotNull { stats ->
                    val pm = context.packageManager
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(stats.packageName, 0)).toString()
                    } catch (e: Exception) {
                        stats.packageName
                    }
                    val icon = try {
                        pm.getApplicationIcon(stats.packageName)
                    } catch (e: Exception) {
                        null
                    }
                    val category = AppCategory.NEUTRAL // You can categorize based on your logic
                    
                    AppInfo(
                        packageName = stats.packageName,
                        appName = appName,
                        icon = icon,
                        category = category,
                        usageTime = stats.totalTimeInForeground // Actual usage time in milliseconds
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    init {
        viewModelScope.launch {
            settingsRepository.isMonitoringEnabled.collect { isEnabled ->
                _isMonitoring.value = isEnabled
                if (isEnabled) {
                    startMonitoring()
                } else {
                    stopMonitoring()
                }
            }
        }

        viewModelScope.launch {
            distractionDetector.currentSession.collect { session ->
                _currentSession.value = session?.let { serviceSession ->
                    com.example.mindshield.data.model.AppSession(
                        packageName = serviceSession.packageName,
                        appName = serviceSession.appName,
                        startTime = serviceSession.startTime,
                        duration = serviceSession.duration,
                        isDistracting = serviceSession.isDistracting
                    )
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.userName.collect { name ->
                _userName.value = name
            }
        }

        // Real-time updates for Home screen - fetch data more frequently
        viewModelScope.launch {
            while (true) {
                try {
                    _topApps.value = appUsageRepository.getTopApps()
                    _todayOverview.value = appUsageRepository.getTodayOverview()
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error updating real-time data", e)
                }
                kotlinx.coroutines.delay(10_000L) // Update every 10 seconds instead of 5 seconds
            }
        }
        
        viewModelScope.launch {
            appUsageRepository.getRealTimeStats().collect { stats ->
                _realTimeStats.value = stats
                Log.d("HomeViewModel", "Real-time stats: ${stats.usageTime/1000/60}m usage, ${stats.distractions} distractions")
            }
        }
    }

    fun onPermissionsGranted() {
        // Do not auto-enable monitoring. Only user can enable it via toggle.
        // viewModelScope.launch {
        //     settingsRepository.setMonitoringEnabled(true)
        // }
    }

    fun toggleMonitoring() {
        viewModelScope.launch {
            val currentState = settingsRepository.isMonitoringEnabled.first()
            settingsRepository.setMonitoringEnabled(!currentState)
        }
    }

    fun acknowledgeDistraction() {
        viewModelScope.launch {
            currentSession.value?.let { session ->
                distractionEventRepository.recordDistractionEvent(
                    packageName = session.packageName,
                    appName = session.appName,
                    duration = session.duration,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    fun dismissDistraction() {
        viewModelScope.launch {
            distractionDetector.dismissCurrentAlert()
        }
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            // Start the distraction detection service
        }
    }

    private fun stopMonitoring() {
        viewModelScope.launch {
            // Stop the distraction detection service
        }
    }

    fun startDistractionDetection(context: Context) {
        DistractionDetectionService.start(context)
    }

    fun stopDistractionDetection(context: Context) {
        DistractionDetectionService.stop(context)
    }

    override fun onCleared() {
        super.onCleared()
        stopRecentAppsPolling()
        
        // Clean up resources
        cleanupResources()
    }
    
    private fun cleanupResources() {
        // Clear state flows
        _recentApps.value = emptyList()
        _topApps.value = emptyList()
        _todayOverview.value = null
        _realTimeStats.value = null
        _currentSession.value = null
        _currentAppInfo.value = null
        _userName.value = ""
        
        // Clear service references
        monitoringService = null
        currentAppStartTime = 0L
        
        Log.d("HomeViewModel", "HomeViewModel resources cleaned up")
    }
} 