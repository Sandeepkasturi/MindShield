package com.example.mindshield.data.repository

import com.example.mindshield.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    
    val userName: Flow<String> = settingsDataStore.userName
    
    val isMonitoringEnabled: Flow<Boolean> = settingsDataStore.isMonitoringEnabled
    
    val alertDelayMinutes: Flow<Int> = settingsDataStore.alertDelayMinutes
    
    val alertSoundEnabled: Flow<Boolean> = settingsDataStore.alertSoundEnabled
    
    val vibrationEnabled: Flow<Boolean> = settingsDataStore.vibrationEnabled
    
    val focusModeEnabled: Flow<Boolean> = settingsDataStore.focusModeEnabled
    
    val focusModeDuration: Flow<Int> = settingsDataStore.focusModeDuration
    
    val distractingApps: Flow<Set<String>> = settingsDataStore.distractingApps
    
    // Add content aware detection toggle
    val contentAwareEnabled: Flow<Boolean> = settingsDataStore.contentAwareEnabled
    
    // App timer settings
    val appTimerEnabled: Flow<Boolean> = settingsDataStore.appTimerEnabled
    
    // Add Gemini API key support
    val geminiApiKey: Flow<String> = settingsDataStore.geminiApiKey
    suspend fun setGeminiApiKey(key: String) {
        settingsDataStore.setGeminiApiKey(key)
    }
    
    suspend fun initializeDefaultsIfNeeded() {
        val defaultApps = setOf(
            "com.facebook.katana", // Facebook
            "com.instagram.android", // Instagram
            "com.twitter.android", // Twitter
            "com.snapchat.android", // Snapchat
            "com.whatsapp", // WhatsApp
            "com.discord", // Discord
            "com.reddit.frontpage", // Reddit
            "com.zhiliaoapp.musically", // TikTok
            "com.google.android.youtube", // YouTube
            "com.netflix.mediaclient", // Netflix
            "com.spotify.music", // Spotify
            "com.epicgames.fortnite", // Fortnite
            "com.activision.callofduty.shooter", // Call of Duty
            "com.ea.gp.apexlegendsmobilefps", // Apex Legends
            "com.roblox.client", // Roblox
            "com.mojang.minecraftpe", // Minecraft
            "com.nianticlabs.pokemongo", // Pokémon GO
            "com.supercell.clashofclans", // Clash of Clans
            "com.supercell.clashroyale", // Clash Royale
            "com.king.candycrushsaga" // Candy Crush
        )
        val currentApps = settingsDataStore.distractingApps.first()
        if (currentApps.isEmpty()) {
            defaultApps.forEach { packageName ->
                settingsDataStore.addDistractingApp(packageName)
            }
        }
    }
    
    suspend fun setUserName(name: String) {
        settingsDataStore.setUserName(name)
    }
    
    suspend fun setMonitoringEnabled(enabled: Boolean) {
        settingsDataStore.setMonitoringEnabled(enabled)
    }
    
    suspend fun setAlertDelayMinutes(minutes: Int) {
        settingsDataStore.setAlertDelayMinutes(minutes)
    }
    
    suspend fun setAlertSoundEnabled(enabled: Boolean) {
        settingsDataStore.setAlertSoundEnabled(enabled)
    }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        settingsDataStore.setVibrationEnabled(enabled)
    }
    
    suspend fun setFocusModeEnabled(enabled: Boolean) {
        settingsDataStore.setFocusModeEnabled(enabled)
    }
    
    suspend fun setFocusModeDuration(minutes: Int) {
        settingsDataStore.setFocusModeDuration(minutes)
    }
    
    suspend fun addDistractingApp(packageName: String) {
        settingsDataStore.addDistractingApp(packageName)
    }
    
    suspend fun removeDistractingApp(packageName: String) {
        settingsDataStore.removeDistractingApp(packageName)
    }
    
    suspend fun clearAllSettings() {
        settingsDataStore.clearAllSettings()
    }

    suspend fun setContentAwareEnabled(enabled: Boolean) {
        settingsDataStore.setContentAwareEnabled(enabled)
    }
    
    suspend fun setAppTimerEnabled(enabled: Boolean) {
        settingsDataStore.setAppTimerEnabled(enabled)
    }
} 