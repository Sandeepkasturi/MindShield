package com.example.mindshield.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mindshield_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private object PreferencesKeys {
        val USER_NAME = stringPreferencesKey("user_name")
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val ALERT_DELAY_MINUTES = intPreferencesKey("alert_delay_minutes")
        val ALERT_SOUND_ENABLED = booleanPreferencesKey("alert_sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val FOCUS_MODE_ENABLED = booleanPreferencesKey("focus_mode_enabled")
        val FOCUS_MODE_DURATION = intPreferencesKey("focus_mode_duration")
        val DISTRACTING_APPS = stringSetPreferencesKey("distracting_apps")
        // Add content aware detection toggle key
        val CONTENT_AWARE_ENABLED = booleanPreferencesKey("content_aware_enabled")
        // App timer settings
        val APP_TIMER_ENABLED = booleanPreferencesKey("app_timer_enabled")
        // Add Gemini API key
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }
    
    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_NAME] ?: ""
    }
    
    val isMonitoringEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_MONITORING_ENABLED] ?: false
    }
    
    val alertDelayMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ALERT_DELAY_MINUTES] ?: 5
    }
    
    val alertSoundEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ALERT_SOUND_ENABLED] ?: true
    }
    
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VIBRATION_ENABLED] ?: true
    }
    
    val focusModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FOCUS_MODE_ENABLED] ?: false
    }
    
    val focusModeDuration: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FOCUS_MODE_DURATION] ?: 30
    }
    
    val distractingApps: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DISTRACTING_APPS] ?: emptySet()
    }
    
    val contentAwareEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CONTENT_AWARE_ENABLED] ?: false
    }
    
    val appTimerEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_TIMER_ENABLED] ?: false
    }
    
    // Add Flow for Gemini API key
    val geminiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GEMINI_API_KEY] ?: ""
    }
    
    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
        }
    }
    
    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_MONITORING_ENABLED] = enabled
        }
    }
    
    suspend fun setAlertDelayMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_DELAY_MINUTES] = minutes
        }
    }
    
    suspend fun setAlertSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_SOUND_ENABLED] = enabled
        }
    }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] = enabled
        } }
    
    suspend fun setFocusModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOCUS_MODE_ENABLED] = enabled
        }
    }
    
    suspend fun setFocusModeDuration(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOCUS_MODE_DURATION] = minutes
        }
    }
    
    suspend fun addDistractingApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[PreferencesKeys.DISTRACTING_APPS] ?: emptySet()
            preferences[PreferencesKeys.DISTRACTING_APPS] = currentApps + packageName
        }
    }
    
    suspend fun removeDistractingApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[PreferencesKeys.DISTRACTING_APPS] ?: emptySet()
            preferences[PreferencesKeys.DISTRACTING_APPS] = currentApps - packageName
        }
    }
    
    suspend fun clearAllSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun setContentAwareEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTENT_AWARE_ENABLED] = enabled
        }
    }
    
    suspend fun setAppTimerEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_TIMER_ENABLED] = enabled
        }
    }

    // Add setter for Gemini API key
    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_API_KEY] = key
        }
    }
} 