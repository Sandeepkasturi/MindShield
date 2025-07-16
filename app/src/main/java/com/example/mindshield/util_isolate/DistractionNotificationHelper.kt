package com.example.mindshield.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mindshield.R
import android.annotation.SuppressLint

object DistractionNotificationHelper {
    private const val CHANNEL_ID = "distraction_warning"
    private const val CHANNEL_NAME = "Distraction Warnings"
    private const val NOTIFICATION_ID = 2001
    private const val ACTION_DISMISS = "com.example.mindshield.ACTION_DISMISS_WARNING"
    private const val ACTION_EXIT = "com.example.mindshield.ACTION_EXIT"

    private var dismissListener: (() -> Unit)? = null
    private var dismissReceiver: BroadcastReceiver? = null
    private var registered = false

    // Remove or comment out all NotificationCompat.Builder, notify, and showWarningNotification logic for distraction alerts.
    // Only use in-app overlays for all distraction events.

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun showYouTubeDistractionNotification(context: Context, userName: String, onDismiss: () -> Unit, onExit: () -> Unit) {
        val dismissIntent = Intent(ACTION_DISMISS)
        val exitIntent = Intent(ACTION_EXIT)
        val pendingDismiss = PendingIntent.getBroadcast(
            context,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val pendingExit = PendingIntent.getBroadcast(
            context,
            2,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        // Register receiver for both actions
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DISMISS -> {
                        onDismiss()
                        clearNotification(context)
                    }
                    ACTION_EXIT -> {
                        onExit()
                        clearNotification(context)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.registerReceiver(receiver, IntentFilter(ACTION_DISMISS), Context.RECEIVER_NOT_EXPORTED)
        context.registerReceiver(receiver, IntentFilter(ACTION_EXIT), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_DISMISS))
            context.registerReceiver(receiver, IntentFilter(ACTION_EXIT))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("Hey $userName, you are now watching unproductive/entertainment content")
            .setContentText("You searched for entertainment content. What would you like to do?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOngoing(false)
            .setColor(Color.YELLOW)
            .addAction(R.drawable.ic_warning, "Dismiss (+1 min)", pendingDismiss)
            .addAction(R.drawable.ic_warning, "Exit", pendingExit)
        // Remove all system notification logic for distraction alerts/blocks.
        // Only use in-app overlays for all distraction events.
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerDismissReceiver(context: Context) {
        if (registered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ACTION_DISMISS) {
                    dismissListener?.invoke()
                    dismissListener = null
                    unregister(context)
                    // Remove all system notification logic for distraction alerts/blocks.
                    // Only use in-app overlays for all distraction events.
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.registerReceiver(receiver, IntentFilter(ACTION_DISMISS), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_DISMISS))
        }
        dismissReceiver = receiver
        registered = true
    }

    private fun unregister(context: Context) {
        try {
            dismissReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        dismissReceiver = null
        registered = false
    }

    fun clearNotification(context: Context) {
        // Remove all system notification logic for distraction alerts/blocks.
        // Only use in-app overlays for all distraction events.
        unregister(context)
    }
} 