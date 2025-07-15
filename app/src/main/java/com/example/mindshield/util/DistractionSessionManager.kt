package com.example.mindshield.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object DistractionSessionManager {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentApp: String? = null
    private var sessionStartTime: Long = 0L
    private var warningShown = false
    private var blockActive = false
    private var blockEndTime: Long = 0L
    private var postWarningStartTime: Long = 0L
    private var notificationDismissed = false
    private var blockCallback: ((String) -> Unit)? = null
    private var unblockCallback: ((String) -> Unit)? = null
    private var warningCallback: ((String) -> Unit)? = null
    private var notificationDismissCallback: ((String) -> Unit)? = null
    private var notificationCount = 0
    private var overlayActive = false
    private var overlayFinishCallback: ((String) -> Unit)? = null
    private val blockDurationMs = 60_000L // 1 minute
    private val warningDelayMs = 3 * 60_000L // 3 minutes
    private val postWarningLimitMs = 5 * 60_000L // 5 minutes
    private val handler = Handler(Looper.getMainLooper())
    private var sessionJob: Job? = null

    fun setCallbacks(
        onBlock: (String) -> Unit,
        onUnblock: (String) -> Unit,
        onWarning: (String) -> Unit,
        onNotificationDismiss: (String) -> Unit,
        onOverlayFinish: (String) -> Unit // new callback
    ) {
        blockCallback = onBlock
        unblockCallback = onUnblock
        warningCallback = onWarning
        notificationDismissCallback = onNotificationDismiss
        overlayFinishCallback = onOverlayFinish
    }

    fun onAppOpened(context: Context, packageName: String) {
        if (overlayActive) {
            // If overlay is active, re-show overlay and restart timer
            overlayFinishCallback?.invoke(packageName)
            return
        }
        if (currentApp != packageName) {
            resetSession()
            currentApp = packageName
            sessionStartTime = System.currentTimeMillis()
            warningShown = false
            notificationDismissed = false
            postWarningStartTime = 0L
            notificationCount = 0
            overlayActive = false
            startSessionTimer(context, packageName)
        }
    }

    private fun startSessionTimer(context: Context, packageName: String) {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            var notified1 = false
            var notified2 = false
            var overlayStarted = false
            while (true) {
                val now = System.currentTimeMillis()
                val elapsed = now - sessionStartTime
                if (!notified1 && elapsed >= 10_000L) {
                    warningCallback?.invoke(packageName)
                    notificationCount++
                    notified1 = true
                }
                if (!notified2 && elapsed >= 20_000L) {
                    warningCallback?.invoke(packageName)
                    notificationCount++
                    notified2 = true
                }
                if (!overlayStarted && elapsed >= 30_000L) {
                    overlayActive = true
                    blockCallback?.invoke(packageName)
                    // Start overlay countdown (30s)
                    handler.postDelayed({
                        overlayActive = false
                        unblockCallback?.invoke(packageName)
                        overlayFinishCallback?.invoke(packageName)
                    }, 30_000L)
                    overlayStarted = true
                    break // Stop session timer after overlay starts
                }
                delay(500L)
            }
        }
    }

    fun onNotificationDismissed(packageName: String) {
        if (currentApp == packageName && warningShown && !notificationDismissed) {
            notificationDismissed = true
            postWarningStartTime = System.currentTimeMillis()
            notificationDismissCallback?.invoke(packageName)
        }
    }

    fun isBlocked(packageName: String): Boolean {
        return blockActive && currentApp == packageName && System.currentTimeMillis() < blockEndTime
    }

    fun getBlockRemainingMs(): Long {
        return if (blockActive) blockEndTime - System.currentTimeMillis() else 0L
    }

    fun resetSession() {
        sessionJob?.cancel()
        currentApp = null
        sessionStartTime = 0L
        warningShown = false
        notificationDismissed = false
        postWarningStartTime = 0L
        notificationCount = 0
        overlayActive = false
    }
} 