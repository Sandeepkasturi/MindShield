package com.example.mindshield.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.mindshield.DistractionOverlay
import com.example.mindshield.data.model.AppSession
import com.example.mindshield.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistractionAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    
    private var currentOverlay: DistractionOverlay? = null
    
    suspend fun showAlert(session: AppSession) {
        val soundEnabled = settingsRepository.alertSoundEnabled.first()
        val vibrationEnabled = settingsRepository.vibrationEnabled.first()
        
        // Create and show overlay
        currentOverlay = DistractionOverlay(context).apply {
            setSession(session)
            setSoundEnabled(soundEnabled)
            setVibrationEnabled(vibrationEnabled)
            show()
        }
    }
    
    fun dismissAlert() {
        currentOverlay?.dismiss()
        currentOverlay = null
    }
    
    fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
} 