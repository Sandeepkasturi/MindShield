package com.example.mindshield.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mindshield.R
import com.example.mindshield.service.ContentAnalyzerReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistractionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "distraction_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_DISMISS = "com.example.mindshield.ACTION_DISMISS"
        private const val ACTION_EXIT = "com.example.mindshield.ACTION_EXIT"
    }

    init {
        createNotificationChannel()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ContentAnalyzerReceiver.ACTION_CLASSIFICATION_RESULT -> {
                val action = intent.getStringExtra(ContentAnalyzerReceiver.EXTRA_ACTION)
                if (action == "ALERT") {
                    showDistractionAlert()
                }
            }
            ACTION_DISMISS -> {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
            ACTION_EXIT -> {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                // Optionally, trigger overlay block here
                DistractionBlockOverlay.show(
                    context,
                    "Unproductive Session",
                    30_000L,
                    onDismiss = {
                        // Dismiss logic if any
                    }
                )
            }
        }
    }

    fun showDistractionAlert() {
        val dismissIntent = Intent(ACTION_DISMISS).let {
            PendingIntent.getBroadcast(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val exitIntent = Intent(ACTION_EXIT).let {
            PendingIntent.getBroadcast(context, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("Distraction Detected!")
            .setContentText("You are engaging in unproductive content. Take action?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_warning, "Dismiss", dismissIntent)
            .addAction(R.drawable.ic_warning, "Exit & Block", exitIntent)
            .setAutoCancel(true)
        try {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Handle permission denied gracefully
            android.util.Log.w("DistractionNotificationManager", "Notification permission denied: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Distraction Alerts"
            val descriptionText = "Notifications for distraction alerts and blocking."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
} 