package com.example.mindshield.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshield.data.repository.SettingsRepository
import com.example.mindshield.util.DistractionAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import com.example.mindshield.util.DistractionNotificationHelper
import com.example.mindshield.data.repository.AppInfoRepository
import com.example.mindshield.data.repository.AppTimerRepository
import com.example.mindshield.data.model.AppInfoEntity
import com.example.mindshield.data.model.AppTimer

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appInfoRepository: AppInfoRepository,
    private val appTimerRepository: AppTimerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Content-aware detection toggle state
    private val _contentAwareEnabled = MutableStateFlow(false)
    val contentAwareEnabled: StateFlow<Boolean> = _contentAwareEnabled.asStateFlow()
    
    // App timer state
    private val _appTimerEnabled = MutableStateFlow(false)
    val appTimerEnabled: StateFlow<Boolean> = _appTimerEnabled.asStateFlow()
    
    private val _appTimers = MutableStateFlow<List<AppTimer>>(emptyList())
    val appTimers: StateFlow<List<AppTimer>> = _appTimers.asStateFlow()

    // Gemini API key state
    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun setContentAwareEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setContentAwareEnabled(enabled)
            _contentAwareEnabled.value = enabled
        }
    }
    
    fun setAppTimerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAppTimerEnabled(enabled)
            _appTimerEnabled.value = enabled
        }
    }

    fun updateGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        viewModelScope.launch {
            settingsRepository.setGeminiApiKey(key)
        }
    }

    init {
        loadSettings()
        // Load content aware detection toggle from repository
        viewModelScope.launch {
            settingsRepository.contentAwareEnabled.collect { enabled ->
                _contentAwareEnabled.value = enabled
            }
        }
        
        // Load app timer settings from repository
        viewModelScope.launch {
            settingsRepository.appTimerEnabled.collect { enabled ->
                _appTimerEnabled.value = enabled
            }
        }
        
        // Load app timers from repository
        viewModelScope.launch {
            appTimerRepository.getAllAppTimers().collect { timers ->
                _appTimers.value = timers
            }
        }

        // Load Gemini API key from repository
        viewModelScope.launch {
            settingsRepository.geminiApiKey.collect { key ->
                _geminiApiKey.value = key
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Collect each setting individually to avoid combine type inference issues
            settingsRepository.userName.collect { userName ->
                val isMonitoring = settingsRepository.isMonitoringEnabled.first()
                val alertDelay = settingsRepository.alertDelayMinutes.first()
                val soundEnabled = settingsRepository.alertSoundEnabled.first()
                val vibrationEnabled = settingsRepository.vibrationEnabled.first()
                val focusMode = settingsRepository.focusModeEnabled.first()
                val focusDuration = settingsRepository.focusModeDuration.first()
                val distractingApps = settingsRepository.distractingApps.first()

                // Load app info from database
                val appInfoEntities = appInfoRepository.getAllAppInfo()
                val appInfoMap = appInfoEntities.associateBy { it.packageName }

                _uiState.value = SettingsUiState(
                    userName = userName,
                    isMonitoringEnabled = isMonitoring,
                    alertDelayMinutes = alertDelay,
                    alertSoundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    focusModeEnabled = focusMode,
                    focusModeDuration = focusDuration,
                    distractingApps = distractingApps.map { packageName ->
                        val entity = appInfoMap[packageName]
                        AppInfo(
                            packageName = packageName,
                            appName = entity?.appName ?: getAppName(packageName),
                            isDistracting = true
                        )
                    },
                    appVersion = "1.0.0",
                    buildNumber = "1"
                )
            }
        }
    }

    fun updateUserName(name: String) {
        // Immediately update the UI state for responsiveness
        _uiState.update { it.copy(userName = name) }
        // Persist the change in the background
        viewModelScope.launch {
            settingsRepository.setUserName(name)
        }
    }

    fun toggleMonitoring() {
        viewModelScope.launch {
            val currentState = settingsRepository.isMonitoringEnabled.first()
            settingsRepository.setMonitoringEnabled(!currentState)
        }
    }

    fun updateAlertDelay(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAlertDelayMinutes(minutes)
        }
    }

    fun toggleAlertSound() {
        viewModelScope.launch {
            val currentState = settingsRepository.alertSoundEnabled.first()
            settingsRepository.setAlertSoundEnabled(!currentState)
        }
    }

    fun toggleVibration() {
        viewModelScope.launch {
            val currentState = settingsRepository.vibrationEnabled.first()
            settingsRepository.setVibrationEnabled(!currentState)
        }
    }

    fun toggleFocusMode() {
        viewModelScope.launch {
            val currentState = settingsRepository.focusModeEnabled.first()
            settingsRepository.setFocusModeEnabled(!currentState)
        }
    }

    fun updateFocusModeDuration(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setFocusModeDuration(minutes)
        }
    }

    fun updateAppDistractionStatus(context: Context, packageName: String, isDistracting: Boolean) {
        viewModelScope.launch {
            if (isDistracting) {
                settingsRepository.addDistractingApp(packageName)
                DistractionAppRepository.addUserDistractionApp(context, packageName)
                // Check if app is currently open
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 5000
                val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
                val event = UsageEvents.Event()
                var isAppOpen = false
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.packageName == packageName) {
                        isAppOpen = true
                    }
                }
                if (isAppOpen) {
                    // Get app name for notification
                    val appName = getAppName(packageName)
                    // Remove or comment out the call to DistractionNotificationHelper.showWarningNotification(context, appName) {}
                    // If needed, replace with in-app overlay logic or leave blank.
                }
            } else {
                settingsRepository.removeDistractingApp(packageName)
                DistractionAppRepository.removeUserDistractionApp(context, packageName)
            }
        }
    }

    fun exportData() {
        viewModelScope.launch {
            // Implementation for data export
            // This would typically involve creating a CSV or JSON file
            // and sharing it via Android's share intent
        }
    }

    fun clearData() {
        viewModelScope.launch {
            // Implementation for clearing all app data
            // This would typically involve clearing the database
            // and resetting all settings to defaults
        }
    }
    
    // App timer functions
    fun addAppTimer(packageName: String, appName: String, dailyLimitMinutes: Int) {
        viewModelScope.launch {
            val appTimer = AppTimer(
                packageName = packageName,
                appName = appName,
                dailyLimitMinutes = dailyLimitMinutes
            )
            appTimerRepository.insertAppTimer(appTimer)
        }
    }
    
    fun updateAppTimer(appTimer: AppTimer) {
        viewModelScope.launch {
            appTimerRepository.updateAppTimer(appTimer)
        }
    }
    
    fun deleteAppTimer(appTimer: AppTimer) {
        viewModelScope.launch {
            appTimerRepository.deleteAppTimer(appTimer)
        }
    }
    
    fun toggleAppTimerEnabled(appTimer: AppTimer) {
        viewModelScope.launch {
            val updatedTimer = appTimer.copy(isEnabled = !appTimer.isEnabled)
            appTimerRepository.updateAppTimer(updatedTimer)
        }
    }

    fun insertOrUpdateAppInfo(appInfo: AppInfo) {
        viewModelScope.launch {
            val entity = AppInfoEntity(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                iconUri = null, // Handle icon persistence if needed
                category = "NEUTRAL", // Or map from your logic
                usageTime = 0L // Or set as needed
            )
            appInfoRepository.insertAppInfo(entity)
        }
    }

    private fun getAppName(packageName: String): String {
        // This would typically query the package manager to get the app name
        // For now, return a simplified version
        return packageName.split(".").last().replaceFirstChar { it.uppercase() }
    }
}

data class SettingsUiState(
    val userName: String = "",
    val isMonitoringEnabled: Boolean = false,
    val alertDelayMinutes: Int = 5,
    val alertSoundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val focusModeEnabled: Boolean = false,
    val focusModeDuration: Int = 30,
    val distractingApps: List<AppInfo> = emptyList(),
    val appVersion: String = "1.0.0",
    val buildNumber: String = "1",
    val appTimerEnabled: Boolean = false,
    val appTimers: List<AppTimer> = emptyList()
)

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isDistracting: Boolean
) 