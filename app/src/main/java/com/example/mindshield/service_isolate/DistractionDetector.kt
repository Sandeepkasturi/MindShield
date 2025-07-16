package com.example.mindshield.service

import android.content.Context
import android.content.pm.PackageManager
import com.example.mindshield.data.model.AppSession
import com.example.mindshield.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import android.app.PendingIntent
import android.content.Intent
import android.os.Build

@Singleton
class DistractionDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val distractionAlertManager: DistractionAlertManager
) {

    private val packageManager = context.packageManager
    // Map to hold tracking jobs, keyed by package name.
    // This allows cancelling specific jobs when an app is stopped or a new session starts for it.
    private var trackingJobs = mutableMapOf<String, Job>()

    // MutableStateFlow to hold the current active AppSession, if any.
    // It's exposed as a read-only StateFlow.
    private val _currentSession = MutableStateFlow<AppSession?>(null)
    val currentSession: StateFlow<AppSession?> = _currentSession.asStateFlow()

    /**
     * Starts tracking an application for potential distraction.
     * A delay is initiated, and if the app is still active after the delay, an alert is triggered.
     *
     * @param packageName The package name of the application to track.
     * @param startTime The timestamp (in milliseconds) when the application started being tracked.
     */
    fun startTracking(packageName: String, startTime: Long) {
        trackingJobs[packageName]?.cancel()
        trackingJobs[packageName] = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Wait 20 seconds, then push notification if still tracking
                delay(20_000L)
                if (!trackingJobs.containsKey(packageName)) return@launch
                val appName = getAppName(packageName)
                // Push notification (use system notification for now)
                pushDistractionNotification(packageName, appName)
                // Wait until 5 minutes from start
                val timeSinceStart = System.currentTimeMillis() - startTime
                if (timeSinceStart < 5 * 60 * 1000L) {
                    delay(5 * 60 * 1000L - timeSinceStart)
                }
                if (!trackingJobs.containsKey(packageName)) return@launch
                // Show blocking overlay
                val duration = (System.currentTimeMillis() - startTime) / 1000 / 60
                val session = AppSession(
                    packageName = packageName,
                    appName = appName,
                    startTime = startTime,
                    duration = duration,
                    isDistracting = true
                )
                _currentSession.value = session
                distractionAlertManager.showAlert(session)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                trackingJobs.remove(packageName)
            }
        }
    }

    /**
     * Stops tracking a specific application.
     * This cancels the associated tracking coroutine.
     *
     * @param packageName The package name of the application to stop tracking.
     */
    fun stopTracking(packageName: String) {
        // Cancel the coroutine associated with this package name.
        trackingJobs[packageName]?.cancel()
        // Remove the job from the map.
        trackingJobs.remove(packageName)

        // If the current active session belongs to the app being stopped, clear it.
        _currentSession.value?.let { session ->
            if (session.packageName == packageName) {
                _currentSession.value = null
            }
        }
    }

    /**
     * Dismisses the currently active distraction alert.
     * This clears the current session and instructs the alert manager to dismiss the UI alert.
     */
    fun dismissCurrentAlert() {
        _currentSession.value = null // Clear the current session state
        distractionAlertManager.dismissAlert() // Instruct the manager to dismiss the UI alert
    }

    /**
     * Retrieves the human-readable application name for a given package name.
     *
     * @param packageName The package name of the application.
     * @return The application name, or the package name if the name cannot be found.
     */
    private fun getAppName(packageName: String): String {
        return try {
            // Get application information using PackageManager.
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            // Get the application label (name) from the application info.
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // If the package name is not found, return the package name itself as a fallback.
            packageName
        }
    }

    private fun pushDistractionNotification(packageName: String, appName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "distraction_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Distraction Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        val intent = Intent(context, Class.forName("com.example.mindshield.MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Distraction Detected")
            .setContentText("You are using distracting app: $appName")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(packageName.hashCode(), notification)
    }
}
